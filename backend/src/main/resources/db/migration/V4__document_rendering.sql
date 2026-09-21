-- REQ-004 문서 읽기. 변환 결과를 담을 자리를 만든다.

-- 문서 안의 링크를 서비스 경로로 바꾼 결과다. 링크 해소는 같은 snapshot의 다른 문서를 알아야
-- 가능하므로 변환 시점에 한 번 만들어 두고 조회 때 다시 계산하지 않는다.
alter table documents add column links_json text not null default '[]';

-- 변환에 성공한 문서는 valid다. 수집만 끝난 collected 상태는 변환기가 붙기 전의 값이며,
-- 이미 저장된 행을 위해 남겨 둔다. 변환 규칙 버전이 올라가면 그 행들은 새 게시본으로 다시 만들어진다.
alter table documents drop constraint documents_state_check;
alter table documents add constraint documents_state_check
    check (state in ('collected', 'valid', 'invalid'));
