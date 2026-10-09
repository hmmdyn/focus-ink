# 참고 앱과 근거 (2026-10-09)

전체 정리(앱별 표, 근거 표, 출처)는 Claude Docs 문서 "Focus Ink: 참고 앱 UX와 근거 정리"에 있습니다. 이 파일은 코드에 무엇이 왜 들어갔는지만 짧게 남깁니다.

## 근거 등급

- **강함**: 메타분석이나 대규모 무작위 실험에서 반복 확인
- **보통**: 잘 설계된 연구 1~2편, 또는 효과가 작거나 조건이 붙음
- **약함**: 연구가 적거나 결과가 엇갈림
- **실무**: 여러 앱이 쓰지만 통제된 연구는 없음

## 코드에 반영한 것

| 기능 | 근거 | 등급 | 코드 |
| --- | --- | --- | --- |
| 집중 시작 때 "딴짓하고 싶어지면 → 이렇게 한다" | [Gollwitzer & Sheeran 2006](https://kops.uni-konstanz.de/entities/publication/2e749bfb-8533-437c-8203-7e788c910c5f), [Wieber 2011](https://www.socmot.uni-konstanz.de/publications/if-then-planning-helps-school-aged-children-ignore-attractive-distractions) | 강함 | `ActiveSession.ifThen`, `Focus.recentIfThens` |
| 끝낼 때 "다음에 이어서 할 일", 다음 준비 때 다시 보여 주기 | [Leroy & Glomb 2018](https://ideas.repec.org/a/inm/ororsc/v29y2018i3p380-397.html), Session 앱 | 보통 | `FocusSession.nextStep`, `Focus.resumeNote` |
| 쉬는 시간을 집중 길이에 맞춤 | [Albulescu 외 2022](https://doaj.org/article/c6f37b6ba159469296b2cb968b65505f) | 보통 | `Focus.breakMinutes` |
| 습관 주 1회 쉬어 가기 | [Sharif & Shu](https://anderson-review.ucla.edu/emergency-reserves/), [Silverman & Barasch 2023](https://www.colorado.edu/business/faculty-research/2023/04/19/or-track-how-broken-streaks-affect-consumer-decisions), Loop·Habitica | 보통~강함 | `AppState.habitRests`, `Habits.toggleRest` |
| 익숙해진 정도 점수 | [Lally 외 2010](https://doi.org/10.1002/ejsp.674), [Loop](https://github.com/iSoron/uhabits/discussions/689) | 보통 | `Habits.strength` |
| 다시 돌아온 날 알아봐 주기 | [Milkman 외 2021](https://doi.org/10.1038/s41586-021-04128-4) | 강함(단기) | `Habits.cameBack` |
| 월요일·1일 새 출발 안내 | [Dai, Milkman & Riis 2014](https://faculty.wharton.upenn.edu/wp-content/uploads/2014/06/Dai_Fresh_Start_2014_Mgmt_Sci.pdf) | 보통 | `Habits.isFreshStart`, `HabitStatus.LAPSED` |
| 내일 첫 집중 시각 정하기 | 실행 의도(위), [Webb & Sheeran 2006](https://eprints.whiterose.ac.uk/1580) | 강함 | `DayPlan`, `Plans`, `Guide` |
| 계획 때 지난 7일 평균 보여 주기 | [Buehler, Griffin & Ross 1994](https://bear.warrington.ufl.edu/brenner/mar7588/Papers/buehler-et-al-1994.pdf) | 편향은 강함, 해결책은 약함 | `Stats.dailyAverageMinutes` |
| 터치 영역 48dp | Material 최소 기준 | 실무 | `Ink.kt` |

## 그대로 둔 것

딴짓 기록([Harkin 2016](https://eprints.whiterose.ac.uk/91437/), 강함), 아이폰 "잠깐 멈춤"([Grüning 2023](https://www.pnas.org/doi/10.1073/pnas.2213114120), 강함), 불렛저널식 옮겨 적기, 한 줄 회고([Di Stefano 외](https://www.unibocconi.it/en/news/thinking-about-it-beats-repeating-it), 보통), 처음 15분 두 번 눌러야 멈춤.

## 하지 않은 것

- **코인·캐릭터·순위 같은 보상**: 효과가 작고 몇 달 뒤 거의 사라짐([Mazeas 2022](https://jmir.org/2022/1/e26779)).
- **할 일 개수 강제 제한**: 숫자 제한을 직접 시험한 연구가 없음. 세 가지는 권하기만 함.
- **"휴대폰이 곁에 있기만 해도 집중력이 떨어진다"는 안내**: 최근 메타분석에서 효과가 거의 없거나 작음([Hartanto 2024](https://researchonline.jcu.edu.au/91280/), [Parry 2024](https://research.vu.nl/en/publications/does-the-mere-presence-of-a-smartphone-impact-cognitive-performan/)). 체크 항목은 두되 효과를 주장하지 않음.
- **"90분은 뇌의 주기"라는 설명**: 수면 주기에서 나온 수치로 근거가 약함. 90분은 기본값일 뿐.
