# 정적 자산을 만들어 nginx가 내보낸다. 실행 이미지에 Node와 소스를 남기지 않는다.
FROM node:22-bookworm AS build
WORKDIR /src

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig*.json vite.config.ts index.html ./
COPY src ./src
COPY public ./public
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=build /src/dist /usr/share/nginx/html
# `/api`는 백엔드로 넘기고 나머지는 단일 페이지 앱으로 돌려준다.
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
# localhost는 컨테이너 안에서 ::1로 먼저 풀리는데 nginx는 IPv4에만 붙는다. 주소를 고정한다.
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
    CMD ["sh", "-c", "wget -q -O /dev/null http://127.0.0.1/ || exit 1"]
