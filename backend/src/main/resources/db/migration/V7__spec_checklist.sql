-- 산출물 체크리스트(API-025). 판정은 수집할 때 한 번 하고 게시본에 함께 둔다.
--
-- 별도 표를 두지 않는다. 조회 단위가 게시본 하나이고 항목별로 질의할 요구가 없다.
-- 게시본은 불변이므로 판정도 그 revision에 고정된다.
--
-- 기본값은 미검사이고 이유는 NOT_COMPUTED다. 이 migration 이전에 만든 게시본은 판정한 적이 없다.
-- 판정하지 않은 것을 통과로 보이게 하지 않고, 검사해서 문서가 없던 것과도 구분한다.
alter table document_snapshots
    add column checklist_json text not null default '{"status":"unchecked","uncheckedReason":"NOT_COMPUTED","truncated":false,"findings":[],"types":[]}';
