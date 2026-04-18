#!/bin/bash
# Apply Liquibase migrations for backend services via dynamically generated
# Kubernetes Job + ConfigMap (changelog files bundled from source repo).
# Usage: ./apply-backend-migrations.sh
set -e

NAMESPACE="skd"

apply_backend_migration() {
  local service="$1"     # auth-service, user-service, ...
  local schema="$2"      # auth, users, ...
  local changelog_dir="/home/mattew/SKD/backend/$service/src/main/resources/changelog"
  local cm_name="${schema}-changelog"
  local job_name="${schema}-liquibase"

  echo "=== $service → schema=$schema ==="

  # Recreate ConfigMap from changelog/ directory (flattened, preserving filenames)
  kubectl delete configmap "$cm_name" -n "$NAMESPACE" --ignore-not-found >/dev/null
  kubectl create configmap "$cm_name" -n "$NAMESPACE" \
    --from-file="$changelog_dir/changelog.yaml" \
    --from-file="$changelog_dir/migrations" >/dev/null
  echo "  configmap $cm_name created"

  # Build migrations files list for ConfigMap projection
  # Note: --from-file=changelog.yaml + --from-file=migrations (dir) puts both
  # at the root; Liquibase changelog.yaml references "migrations/xxx.sql" — we
  # need the files under migrations/ subpath. Adjust: mount with subPath, or
  # recreate CM with explicit structure via yaml.

  # Delete old job (else fails on re-apply)
  kubectl delete job "$job_name" -n "$NAMESPACE" --ignore-not-found >/dev/null

  cat <<EOF | kubectl apply -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: $job_name
  namespace: $NAMESPACE
spec:
  backoffLimit: 3
  ttlSecondsAfterFinished: 600
  template:
    spec:
      restartPolicy: OnFailure
      initContainers:
        - name: layout
          image: busybox:1.37
          command:
            - sh
            - -c
            - |
              set -e
              mkdir -p /work/migrations
              cp /cm/changelog.yaml /work/changelog.yaml
              for f in /cm/*.sql; do
                [ -e "\$f" ] && cp "\$f" /work/migrations/
              done
              echo "prepared:"
              ls -la /work/ /work/migrations/
          volumeMounts:
            - { name: cm, mountPath: /cm }
            - { name: work, mountPath: /work }
      containers:
        - name: liquibase
          image: liquibase/liquibase:4.27
          workingDir: /work
          command:
            - sh
            - -c
            - |
              set -e
              if [ ! -f /liquibase/internal/lib/postgresql.jar ]; then
                wget -q -O /liquibase/internal/lib/postgresql.jar \
                  https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar
              fi
              liquibase --classpath=/liquibase/internal/lib/postgresql.jar \
                --url=jdbc:postgresql://postgres:5432/content_agg_db \
                --username=postgres --password=postgres \
                --defaultSchemaName=$schema \
                --changeLogFile=changelog.yaml \
                update
          volumeMounts:
            - { name: work, mountPath: /work }
      volumes:
        - { name: cm, configMap: { name: $cm_name } }
        - { name: work, emptyDir: {} }
EOF
}

apply_backend_migration auth-service auth
apply_backend_migration user-service users
apply_backend_migration user-interactions-service interactions
apply_backend_migration subscription-service subscription
apply_backend_migration feed-service feed

echo ""
echo "=== waiting for jobs ==="
for j in auth-liquibase users-liquibase interactions-liquibase subscription-liquibase feed-liquibase; do
  if kubectl get job "$j" -n "$NAMESPACE" >/dev/null 2>&1; then
    echo "-- $j --"
    kubectl wait --for=condition=complete --timeout=180s job/"$j" -n "$NAMESPACE" || kubectl logs job/"$j" -n "$NAMESPACE" --tail 30
  fi
done

echo ""
echo "=== final schema inventory ==="
kubectl exec -n "$NAMESPACE" postgres-0 -- psql -U postgres -d content_agg_db -c \
  "SELECT schemaname, COUNT(*) FILTER (WHERE tablename !~ '^qrtz') AS app_tables, COUNT(*) FILTER (WHERE tablename ~ '^qrtz') AS qrtz_tables FROM pg_tables WHERE schemaname IN ('auth','users','interactions','subscription','feed','config','data_flow') GROUP BY schemaname ORDER BY schemaname;"
