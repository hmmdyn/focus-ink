# Focus Ink

Boox Palma 2 (Android e-ink)를 **집중 전용 기기**로, 아이폰은 **방해 차단·기록기**로 쓰는 2-기기 집중 시스템.

- **e-ink 앱** (이 저장소): 날짜를 넘기는 수첩 형태의 할 일과 습관, 90분 집중 타이머(처음 15분은 멈출 수 없음), 딴짓하고 싶을 때 10분 기다리기, 딴짓 원인 기록, 하루 마무리(내일 가장 중요한 일 세 가지), 한 주에 딴짓 하나 줄이기.
- **아이폰**: 단축어 6개 + 자동화 6개 + 스크린 타임. 유혹 앱을 열면 5초 멈춤 후 선택, 결과가 e-ink 에 자동 기록된다.
- **연동**: [ntfy.sh](https://ntfy.sh) 무료 토픽을 우체통으로 사용(서버·계정 불필요).

## 화면 (Palma 2 해상도, CI 에뮬레이터에서 매 커밋 자동 촬영)

| 오늘 | 집중 | 딴짓하고 싶을 때 | 돌아보기 |
| --- | --- | --- | --- |
| ![](https://raw.githubusercontent.com/hmmdyn/focus-ink/screenshots/04_today.png) | ![](https://raw.githubusercontent.com/hmmdyn/focus-ink/screenshots/10_focus_running.png) | ![](https://raw.githubusercontent.com/hmmdyn/focus-ink/screenshots/12_focus_urge.png) | ![](https://raw.githubusercontent.com/hmmdyn/focus-ink/screenshots/13_reflect.png) |

| 지난 쪽 | 지난 할 일 정리 | 기록 | 하루 마무리 |
| --- | --- | --- | --- |
| ![](https://raw.githubusercontent.com/hmmdyn/focus-ink/screenshots/05_yesterday_page.png) | ![](https://raw.githubusercontent.com/hmmdyn/focus-ink/screenshots/07_migrate.png) | ![](https://raw.githubusercontent.com/hmmdyn/focus-ink/screenshots/15_record.png) | ![](https://raw.githubusercontent.com/hmmdyn/focus-ink/screenshots/18_review.png) |

## 설치 (Palma 2)

1. Palma 2 브라우저로 **[최신 APK](https://github.com/hmmdyn/focus-ink/releases/download/latest/focus-ink.apk)** 다운로드 → 설치(출처를 알 수 없는 앱 허용).
2. 처음 실행하면 네 단계로 설정합니다(소개, 습관 하나, 알림 시각, 오늘 가장 중요한 일). 알림 권한을 허용해 주세요.
3. Boox 설정 권장:
   - 앱 최적화(E-Ink Center) → Focus Ink: **새로고침 모드 HD 또는 Balanced**, 애니메이션 필터 끔.
   - 앱 동결 해제(배터리 → 백그라운드에서 동결 안 함). 집중 종료와 하루 마무리 알림을 받으려면 필요합니다.
   - 화면 꺼짐 시간 넉넉히(세션 중에는 앱이 화면을 켜 둔다. e-ink 는 정지 화면에 전력을 거의 쓰지 않는다).
4. 업데이트: 같은 링크에서 새 APK 를 받아 덮어 설치. 서명이 고정이라 데이터가 유지된다.

## 아이폰 설정

[docs/IPHONE.md](docs/IPHONE.md): 단축어, 자동화, 스크린 타임을 단계별로 안내합니다.

## 매일 쓰는 법

[docs/WORKFLOW.md](docs/WORKFLOW.md): 아침 15분, 오전 집중 3시간, 저녁 3분, 일요일 10분.

## 설계

- [docs/DESIGN.md](docs/DESIGN.md): 영상 13편의 원칙과 기능의 대응, e-ink 화면 규칙, 문구 규칙
- [docs/SYNC.md](docs/SYNC.md): 아이폰과 e-ink 사이의 메시지 형식

## 개발

```
core/   순수 Kotlin 도메인 로직 + 단위 테스트 (할 일, 습관, 집중, 통계, 명령 해석, 저장, 조사 처리)
app/    Android (Jetpack Compose), e-ink 전용 흑백 컴포넌트
```

- 빌드: `gradle :core:test :app:assembleRelease` (JDK 17, Android SDK 35)
- CI: main 에 푸시하면 GitHub Actions 가 테스트 → APK 빌드 → `latest` 릴리스 갱신
- 데이터: 기기 내 `files/state.json` 하나. 설정 → 백업 내보내기/복원.
