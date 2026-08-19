#!/usr/bin/env bash
#
# k6 EC2에서 측정을 돌린다. 로컬에서 쏘면 인터넷 왕복이 끼어 1차와 조건이 달라진다.
#
#   scripts/perf-k6/k6ec2.sh push
#   scripts/perf-k6/k6ec2.sh run baseline.js baseline-before-20260819 READ_ITERATIONS=30 WARMUP=10
#   scripts/perf-k6/k6ec2.sh run load.js load-before-20260819 VUS=100
#
# ⚠️ EC2 역할에는 S3 권한이 없다. presigned URL로 주고받는다 — IAM을 건드리지 않는 이유다.
set -euo pipefail
INSTANCE=i-05eb81746a575ca47
REGION=ap-northeast-2
BUCKET=elasticbeanstalk-ap-northeast-2-715975222399
PREFIX=perf-k6
HERE="$(cd "$(dirname "$0")" && pwd)"

ssm_run() {  # $1=설명 $2=쉘 명령
  local cid
  cid=$(aws ssm send-command --instance-ids "$INSTANCE" --region "$REGION" \
        --document-name AWS-RunShellScript --comment "$1" \
        --parameters "commands=[\"$2\"]" --timeout-seconds 3600 \
        --query 'Command.CommandId' --output text)
  echo "  SSM $cid — $1" >&2
  while :; do
    sleep 10
    local st
    st=$(aws ssm get-command-invocation --command-id "$cid" --instance-id "$INSTANCE" \
         --region "$REGION" --query 'Status' --output text 2>/dev/null || echo Pending)
    case "$st" in
      Success) break ;;
      Failed|Cancelled|TimedOut) 
        aws ssm get-command-invocation --command-id "$cid" --instance-id "$INSTANCE" \
          --region "$REGION" --query 'StandardErrorContent' --output text >&2
        exit 1 ;;
    esac
  done
  aws ssm get-command-invocation --command-id "$cid" --instance-id "$INSTANCE" \
    --region "$REGION" --query 'StandardOutputContent' --output text
}

case "${1:-}" in
  push)
    cmds=""
    for f in baseline.js load.js active-users.csv scenarios-load.csv scenarios-baseline.csv; do
      aws s3 cp "$HERE/$f" "s3://$BUCKET/$PREFIX/$f" --region "$REGION" >/dev/null
      url=$(aws s3 presign "s3://$BUCKET/$PREFIX/$f" --expires-in 3600 --region "$REGION")
      cmds+="curl -sfS -o /opt/perf/$f '$url' && "
    done
    ssm_run "push" "mkdir -p /opt/perf && ${cmds}ls -l /opt/perf"
    ;;
  run)
    script="$2"; name="$3"; shift 3
    envs="$*"
    base="${script%.js}-summary"
    # 결과는 SSM 표준출력으로 회수한다.
    #
    # ⚠️ presigned PUT을 쓰지 않는 이유: aws-cli 2.36.16의 `s3 presign`은 --http-method를
    #    모른다(실측 ParamValidation 에러). GET용 URL로 PUT하면 403이라 결과가 조용히 안 온다.
    #    EC2 역할에는 S3 권한이 없어 aws s3 cp도 못 쓴다.
    # ⚠️ 직전 실행의 결과 파일을 먼저 지운다. k6가 setup() 예외 등으로 중단되면
    #    handleSummary가 돌지 않아 파일이 안 써지는데, EC2에는 같은 이름의 옛 파일이 남아 있다.
    #    아래 cat이 그대로 성공해서 **개선 전 결과가 개선 후 파일명으로 회수된다** —
    #    "받음"까지 찍히므로 눈치챌 방법이 없다. k6의 종료코드도 tail 파이프가 가린다
    #    (임계값 실패로 중단되지 않게 하려는 의도라 그쪽은 그대로 둔다).
    ssm_run "run $script" "cd /opt/perf && rm -f $base.md $base.json && $envs k6 run $script 2>&1 | tail -80"
    mkdir -p "$HERE/results"
    for ext in md json; do
      # 파일마다 따로 받는다. 한 번에 받으면 SSM 출력 상한(24,000자)에 걸린다.
      out=$(ssm_run "fetch $base.$ext" "cat /opt/perf/$base.$ext")
      case "$out" in
        *"Output truncated"*|"")
          echo "회수 실패: $base.$ext 가 잘렸거나 비었다" >&2; exit 1 ;;
      esac
      printf '%s\n' "$out" > "$HERE/results/$name.$ext"
      echo "  받음: results/$name.$ext ($(wc -c < "$HERE/results/$name.$ext") bytes)" >&2
    done
    ;;
  *) echo "사용법: k6ec2.sh push | run <script.js> <결과이름> [ENV=V ...]" >&2; exit 1 ;;
esac
