alter table document_snapshots add column collection_branch text;
alter table document_snapshots add column collection_docs_root text;
alter table document_snapshots add column comparison_json text;
alter table document_snapshots drop constraint document_snapshots_identity_key;
alter table document_snapshots add constraint document_snapshots_identity_key
 unique(project_id,source_revision,renderer_version,policy_version,collection_branch,collection_docs_root);
alter table document_snapshots add constraint document_snapshots_scope_pair
 check ((collection_branch is null and collection_docs_root is null) or
 (collection_branch is not null and collection_docs_root is not null and length(collection_branch)>0 and length(collection_docs_root)>0));
-- Old unscoped snapshots retain their original uniqueness guarantee.
create unique index document_snapshots_legacy_identity_idx
 on document_snapshots(project_id,source_revision,renderer_version,policy_version)
 where collection_branch is null and collection_docs_root is null;
