FROM node:10.12.0-alpine as sdk

WORKDIR /usr/src/app

COPY package*.json ./

RUN npm install

COPY . .

RUN npm run build -- --base-href /chart/

FROM nginx

# chart is for google load balancer that does not support rewrite
COPY --from=sdk /usr/src/app/dist/fh-chart /usr/share/nginx/html
COPY --from=sdk /usr/src/app/dist/fh-chart /usr/share/nginx/html/chart

COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80
