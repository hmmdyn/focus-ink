# Focus Ink

Boox Palma 2 (Android e-ink)를 **집중 전용 기기**로, 아이폰은 **방해 차단·기록기**로 쓰는 2-기기 집중 시스템.

- **e-ink 앱** (이 저장소): 딥워크 타이머(닻 15분 → 90분 블록 → 회고 → 휴식), 충동 10분 버티기, 방해 기록, 불렛저널(미룬 일 직접 옮겨 적기), 습관(작게·사슬·두 번 연속 금지), 저녁 리뷰(내일 Top 3), 주간 방해 제거.
- **아이폰**: 단축어 6개 + 자동화 6개 + 스크린 타임. 유혹 앱을 열면 5초 멈춤 후 선택, 결과가 e-ink 에 자동 기록된다.
- **연동**: [ntfy.sh](https://ntfy.sh) 무료 토픽을 우체통으로 사용(서버·계정 불필요).

## 설치 (Palma 2)

1. Palma 2 브라우저로 **[최신 APK](https://github.com/hmmdyn/focus-ink/releases/download/latest/focus-ink.apk)** 다운로드 → 설치(출처를 알 수 없는 앱 허용).
2. 처음 실행하면 4단계 설정(원칙 → 습관 하나 → 리뷰·선셋 시각 → 오늘 Top 3). 알림 권한 허용.
3. Boox 설정 권장:
   - 앱 최적화(E-Ink Center) → Focus Ink: **새로고침 모드 HD 또는 Balanced**, 애니메이션 필터 끔.
   - 앱 동결 해제(배터리 → 백그라운드에서 동결 안 함) — 리뷰·블록 종료 알림용.
   - 화면 꺼짐 시간 넉넉히(세션 중에는 앱이 화면을 켜 둔다. e-ink 는 정지 화면에 전력을 거의 쓰지 않는다).
4. 업데이트: 같은 링크에서 새 APK 를 받아 덮어 설치. 서명이 고정이라 데이터가 유지된다.

## 아이폰 설정

[docs/IPHONE.md](docs/IPHONE.md) — 단축어·자동화·스크린 타임 단계별 안내.

## 매일 쓰는 법

[docs/WORKFLOW.md](docs/WORKFLOW.md) — 아침 15분, 오전 딥워크 3시간, 저녁 리뷰 3분, 주간 10분.

## 설계

- [docs/DESIGN.md](docs/DESIGN.md) — 영상 13편의 원칙 → 기능 매핑, e-ink UI 규칙
- [docs/SYNC.md](docs/SYNC.md) — 아이폰 ↔ e-ink 메시지 형식

## 개발

```
core/   순수 Kotlin 도메인 로직 + 단위 테스트 (저널, 습관, 집중 세션, 통계, 명령 파싱, 저장)
app/    Android (Jetpack Compose), e-ink 전용 흑백 컴포넌트
```

- 빌드: `gradle :core:test :app:assembleRelease` (JDK 17, Android SDK 35)
- CI: main 에 푸시하면 GitHub Actions 가 테스트 → APK 빌드 → `latest` 릴리스 갱신
- 데이터: 기기 내 `files/state.json` 하나. 설정 → 백업 내보내기/복원.
