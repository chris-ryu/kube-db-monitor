수강 신청 앱 같은 java application의 db 모니터링 솔루션 개발 중
falllback 코드 작성시 물어보고 진행 할 것.
mocking 코드 작성시 물어보고 진행 할 것.
한글로 답변
docker image 업데이트하면 실행 버전이 최신 버전인지 항상 확인
TDD를 적극적으로 활용
  - 테스트 케이스를 통해 기능 검증
  - 테스트 케이스 유지보수, 필요없는 테스트케이스는 별도 보관. 

서버측 코드 작성시 simulation용 코드작업 물어보고 진행할 것 
디버깅 용도로 만들어진 시뮬레이션, 모킹 코드는 기능 구현 후 반드시 실제 환경으로 삭제, 복구

Agent -> Control Plane -> Dashboard 서비스 레이어를 넘어가는 부분에서 이벤트의 포맷이나 스키마가 변경되면 다음 레이어에서 호환 되는지 항상 확인

kubernetes 이미지 확인 시 localhost에 portforward하지 말고 public dns로 연결. 환경변수가 확실히 전달되도록 