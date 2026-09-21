-- REQ-004 문서 읽기의 첨부. 데이터 설계의 assets를 만든다.

-- 내용은 해시를 열쇠로 한 번만 저장한다. 같은 그림이 여러 게시본에 나와도 사본이 늘지 않는다.
-- 파일 저장소를 따로 두지 않고 DB에 담는다. 자산당 10MB 제안값이면 감당되고
-- 백업과 권한 검사가 한 곳에 모인다. 부하를 확인한 뒤 바꾼다.
create table asset_contents (
    storage_key text        primary key,
    bytes       bytea       not null,
    byte_size   integer     not null,
    created_at  timestamptz not null,
    constraint asset_contents_size_check check (byte_size >= 0)
);

create table assets (
    id          uuid    primary key,
    snapshot_id uuid    not null references document_snapshots (id),
    path        text    not null,
    mime        text    not null,
    bytes_hash  text    not null,
    storage_key text    not null references asset_contents (storage_key),
    byte_size   integer not null,
    constraint assets_path_key unique (snapshot_id, path),
    constraint assets_size_check check (byte_size >= 0),
    -- 실행될 수 있는 형식은 담지 않는다. SVG와 HTML은 여기서 걸린다.
    constraint assets_mime_check check (mime in ('image/png', 'image/jpeg', 'image/webp', 'image/gif'))
);

create index assets_storage_idx on assets (storage_key);
