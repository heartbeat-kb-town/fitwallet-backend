# openapi-spec

**이 브랜치는 CI가 만든다. 직접 고치지 않는다.**

`develop`에 머지될 때마다 백엔드를 띄워 `/v3/api-docs`를 받아 `openapi.json`으로 발행한다
(`.github/workflows/ci.yml`의 `openapi-spec` 잡). 소스는 `develop`에 있다.

프론트엔드는 이 URL을 API 계약의 정본으로 읽는다:

```
https://raw.githubusercontent.com/heartbeat-kb-town/fitwallet-backend/openapi-spec/openapi.json
```
