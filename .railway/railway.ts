import { defineRailway, github, mysql, preserve, project, redis, service, volume } from "railway/iac";

export default defineRailway(() => {
  const Redis = redis("Redis", { region: "sfo" });
  Redis.deploy = { startCommand: "/bin/sh -c \"rm -rf $RAILWAY_VOLUME_MOUNT_PATH/lost+found/ && exec docker-entrypoint.sh redis-server --requirepass $REDIS_PASSWORD --save 60 1 --dir $RAILWAY_VOLUME_MOUNT_PATH\"" };
  Redis.networking = { privateNetworkEndpoint: "redis" };
  const MySQL = mysql("MySQL", { region: "sfo" });
  MySQL.deploy = { startCommand: "docker-entrypoint.sh mysqld --innodb-use-native-aio=0 --disable-log-bin --performance_schema=0 --innodb-buffer-pool-size=1G" };
  MySQL.networking = { privateNetworkEndpoint: "mysql" };
  const redisVolume = volume("redis-volume", { alerts: { usage: { "100": {}, "80": {}, "95": {} } }, allowOnlineResize: true, region: "sfo", sizeMB: 500 });
  const mysqlVolume = volume("mysql-volume", { alerts: { usage: { "100": {}, "80": {}, "95": {} } }, allowOnlineResize: true, region: "sfo", sizeMB: 500 });
  const _12booksWeb = service("12books-web", {
    source: github("irerin07/12books-web"),
    replicas: { "sfo": 1 },
    env: { BACKEND_ORIGIN: preserve(), NODE_ENV: preserve(), PORT: preserve() },
  });
  const _12books = service("12books", {
    source: github("irerin07/12books", { upstreamUrl: "https://github.com/irerin07/12books" }),
    replicas: { "sfo": 1 },
    // Flyway가 마이그레이션을 까는 동안 트래픽이 붙지 않게 한다. 안 걸면 첫 배포의 초기
    // 요청들이 아직 뜨지 않은 앱을 만난다. 이 경로는 인증 없이 열려 있고, actuator는
    // 기본값이라 health 말고는 노출되지 않는다.
    healthcheck: "/actuator/health",
    healthcheckTimeout: 300,
    env: { BOOK_SIGNATURE_SECRET: preserve(), JWT_SECRET: preserve(), KAKAO_REST_API_KEY: preserve(), PORT: preserve(), REFRESH_COOKIE_SAME_SITE: preserve(), SPRING_DATASOURCE_PASSWORD: preserve(), SPRING_DATASOURCE_URL: preserve(), SPRING_DATASOURCE_USERNAME: preserve(), SPRING_DATA_REDIS_HOST: preserve(), SPRING_DATA_REDIS_PASSWORD: preserve(), SPRING_DATA_REDIS_PORT: preserve() },
  });

  return project("humble-contentment", {
    resources: [_12booksWeb, _12books, Redis, MySQL, redisVolume, mysqlVolume],
  });
});
