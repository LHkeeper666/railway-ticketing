#!/bin/bash
set -e

docker_exe="/c/Program Files/Docker/Docker/Docker Desktop.exe"
docker_env_path="/d/code/JAVA/Project/railway-ticketing/docker-env"
sql_path="/d/code/JAVA/Project/railway-ticketing/src/main/resources"
mysql_container_name="railway-ticketing-mysql"
database_name="railway_ticketing"
sql_file=("db_table.sql" "db_data.sql")

#"${docker_exe}"

cd "${docker_env_path}"

# 确保 my.cnf 权限正确
chmod 644 ./mysql/conf/my.cnf

# 全新初始化
rm -rf ./mysql/data
docker compose down
docker compose up -d

# 等 MySQL ready
echo "Waiting for MySQL..."
until docker exec ${mysql_container_name} \
  mysql -uroot -proot \
  --default-character-set=utf8mb4 \
  -e "SELECT 1" >/dev/null 2>&1
do
  sleep 1
done
echo "MySQL ready"

set -e

cd "$sql_path"

# 1. 创建数据库（明确 utf8mb4）
docker exec -i ${mysql_container_name} \
  mysql -uroot -proot \
  --default-character-set=utf8mb4 \
  -e "CREATE DATABASE IF NOT EXISTS \`${database_name}\`
      DEFAULT CHARACTER SET utf8mb4
      COLLATE utf8mb4_unicode_ci;"

echo "开始了"

# 2. 拷贝 SQL 文件
for file in "${sql_file[@]}"; do
    echo "file = ${file}"
    [ -f "$file" ] || continue
    echo "copy $file"
    docker cp "$file" "${mysql_container_name}":/"$file"
done

# 3. 执行 SQL（关键修复点）
for file in "${sql_file[@]}"; do
    [ -f "$file" ] || continue
    echo "execute $file"
    docker exec -i ${mysql_container_name} \
      mysql -uroot -proot \
      --default-character-set=utf8mb4 \
      ${database_name} < "$file"
done

