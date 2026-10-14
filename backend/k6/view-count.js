// 질문 상세 조회수 동시성 재현용 부하 스크립트.
// 매 요청마다 X-Forwarded-For를 다르게 보내어 서로 다른 비로그인 사용자의 첫 조회를 만듦.
// -> 성공한 요청 수 = 기대 조회수이고 실행 후 DB view_count와 비교
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const QID = __ENV.QID || '1';
const RUN = __ENV.RUN || 'r';

export const options = {
  scenarios: {
    hot_question: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 1000), // 목표 TPS
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: 200,
      maxVUs: 1000,
    },
  },
};

export default function () {
  const res = http.get(`${BASE}/api/questions/${QID}`, {
    headers: { 'X-Forwarded-For': `${RUN}-${__VU}-${__ITER}` },
  });
  check(res, { 'status 200': (r) => r.status === 200 });
}
