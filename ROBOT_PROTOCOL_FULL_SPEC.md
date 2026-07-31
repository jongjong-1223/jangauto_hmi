# 장오토 로봇 통신 프로토콜 종합 기술 명세서 (v1.1)

본 문서는 Android 앱(HMI)과 로봇(ROS/Raspberry Pi) 간의 통신 방식 및 데이터 규격을 정의합니다.

## 1. 네트워크 및 연결 (Network & Connection)

### 1.1 서비스 검색 (mDNS/NSD)
- **Service Type**: `_robot._tcp.`
- 앱은 mDNS를 통해 로봇의 IP와 포트를 자동으로 검색합니다.

### 1.2 WebSocket 통신
- **URL**: `ws://{ROBOT_IP}:{PORT}`
- 모든 실시간 제어 및 상태 정보는 WebSocket을 통해 JSON 형식으로 교환됩니다.
- **재연결 정책**: 연결이 끊어질 경우 앱은 3초 간격으로 재연결을 시도합니다.

---

## 2. 데이터 규격 (JSON Message Spec)

### 2.1 상향 링크 (App -> Robot)

#### A. ControlRequest (주기적 하트비트)
- **전송 주기**: 500ms
- **설명**: 로봇은 이 메시지가 1.0초 이상 수신되지 않으면 안전을 위해 자동으로 `STOP` 상태로 전환합니다.
```json
{
  "sw_bits": 16,     // State Bits (STOP: 16, KEY: 8, CAL: 4, ALIGN: 2, RUN: 1)
  "key_bits": 0,      // Joystick (FRONT: 8, BACK: 4, LEFT: 2, RIGHT: 1, STOP: 0)
  "speed_bits": 2,    // Speed Level (FAST: 1, MED: 2, SLOW: 4)
  "video_bit": 0,     // Video Stream ON(1)/OFF(0)
  "safe_bit": 1       // Safe Mode ON(1)/OFF(0)
}
```

#### B. App Acknowledgement (app_ack)
- **설명**: 로봇으로부터 `map_data`를 수신했을 때 보내는 확인 응답입니다.
```json
{
  "type": "app_ack",
  "msg_id": "a1b2c3d4"
}
```

#### C. 명령형 요청 (Command Requests)
- `move`, `poweroff`, `generate_path` 등은 고유한 `msg_id`와 함께 전송됩니다.

#### D. Coverage Path (ㄹ자 경로) 생성 및 선택
- **GenerateCoveragePathRequest**: 다각형 영역을 기반으로 ㄹ자 경로 생성을 요청합니다.
```json
{
  "msg_id": "unique_id",
  "command": "generate_coverage_path",
  "polygon": [{"x": 1.0, "y": 1.0}, ...],
  "edge_safety_dist": [0.5, 0.5, 0.5, 0.5], // 각 선분별 안전 거리 (리스트)
  "robot_radius": 1.1,
  "ridge_spacing": 0.8,
  "headland_length": 2.0,
  "yaw_deg": 0.0
}
```
- **CoveragePathResult**: 로봇으로부터 두 개의 경로 후보(Left/Right start)를 수신합니다.
- **SelectCoveragePathRequest**: 제안된 경로 중 하나를 인덱스(0, 1)로 선택합니다.

---

### 2.2 하향 링크 (Robot -> App)

#### A. RobotStatus (주기적 상태 미러링)
- **전송 주기**: 5Hz (200ms)
- **설명**: 로봇의 현재 상태 및 센서 데이터를 브로드캐스트합니다.
```json
{
  "current_state": "STOP",  // 현재 로봇 상태
  "in_error": false,
  "error_reason": null,
  "tag_x": 0.0,             // 로봇 현재 위치 (Global X)
  "tag_y": 0.0,             // 로봇 현재 위치 (Global Y)
  "tag_ori": 0.0,           // 로봇 헤딩 (Degree)
  "tag_vel": 0.0,           // 현재 속도 (m/s)
  "tag_yaw_rate": 0.0,      // 현재 회전 속도 (rad/s)
  "calibration_complete": false, // 보정 완료 여부
  "path_selected": false    // ㄹ자 경로 선택 여부
}
```

#### B. MapData (신뢰성 전송)
- **설명**: 맵 포인트 및 장애물 정보를 보냅니다. 앱의 `app_ack`가 1.0초 내에 오지 않으면 최대 3회 재시도합니다.
```json
{
  "type": "map_data",
  "msg_id": "unique_id",
  "map": [{"x": 1.0, "y": 1.0}, ...],     // 맵 기준점 리스트
  "obstacles": [[{"x": 2.0, "y": 2.0}, ...], ...] // 장애물 선분 리스트
}
```

---

## 3. 제어 로직 및 규칙 (Control Logic)

### 3.1 상태 전환 규칙 (State Transition)
앱은 다음 규칙에 따라 상태 전환 요청을 필터링합니다:
1.  **자유 그룹**: `STOP`, `KEY`, `CAL` 간에는 자유롭게 전환 가능합니다.
2.  **ALIGN 진입**: `STOP`, `KEY`, `CAL` 상태에서만 `ALIGN`으로 갈 수 있습니다.
3.  **RUN 진입**: 반드시 `ALIGN` 상태를 거쳐야만 `RUN`으로 갈 수 있습니다.
4.  **하향 전환**: 상위 상태에서 하위 상태(예: `RUN` -> `STOP`)로의 이동은 언제나 허용됩니다.

### 3.2 앱 측 타임아웃 처리
- 사용자가 상태 전환을 요청하면 앱은 `Requesting: [STATE]`를 표시합니다.
- **2초** 내에 로봇으로부터 해당 상태가 반영된 `RobotStatus`를 받지 못하면, 앱은 요청이 실패한 것으로 간주하고 `Requesting` 상태를 로봇의 실제 현재 상태로 리셋합니다.

### 3.3 보정 및 경로 상태에 따른 동작 제한
- `calibration_complete`가 `false`인 경우, 로봇은 `move` 명령을 수락하지 않으며 앱의 `MOVE` 버튼이 비활성화됩니다.
- `path_selected`가 `false`인 경우, 앱의 `RUN` 버튼이 비활성화되어 경로 없는 자동 주행을 방지합니다.

---

## 4. 변경 이력 (History)
- **v1.0**: 초기 프로토콜 정의.
- **v1.1**:
    - 용어 통일 (`mode` -> `current_state`).
    - 맵 데이터 키 변경 (`anchors` -> `map`, `walls` -> `obstacles`).
    - 타임스탬프 기반 핑 측정 제거 및 `ControlAck` 메시지 폐기.
    - 앱 측 상태 요청 타임아웃(2초) 로직 명시.
- **v1.2**:
    - `RobotStatus`에 `calibration_complete` 및 `path_selected` 필드 추가.
    - 보정 미완료 시 `move` 제한, 경로 미선택 시 `RUN` 제한 정책 추가.
    - Coverage Path (ㄹ자 경로) 생성 및 선택 프로토콜 추가.
    - `edge_safety_dist`를 단일 값에서 리스트(선분별 설정)로 변경.
