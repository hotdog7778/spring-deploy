#!/bin/bash

NGINX_CONTAINER_NAME="zerodowntimepipeline-tomcat-nginx-1"
NGINX_CONFIG_PATH="/Users/tgkim/jenkins/ZeroDowntimePipeline-tomcat/conf/nginx/nginx.conf"
NGINX_CONFIG_BAK_PATH="/Users/tgkim/jenkins/ZeroDowntimePipeline-tomcat/conf/nginx/nginx.bak"


#echo "Nginx 설정 검증 중..."
#/usr/local/bin/docker exec "${NGINX_CONTAINER_NAME}" nginx -t || {
#    echo "Nginx 설정 검증 실패! 복구 중..."
#    cp ${NGINX_CONFIG_BAK_PATH} ${NGINX_CONFIG_PATH}
#    /usr/local/bin/docker exec "${NGINX_CONTAINER_NAME}" nginx -t && echo "복구 완료."
#    exit 1
#}

# Nginx Reload
echo "Reloading Nginx..."
#/usr/local/bin/docker exec "$NGINX_CONTAINER_NAME" nginx -s reload
docker exec "$NGINX_CONTAINER_NAME" nginx -s reload
#docker-compose exec "nginx" nginx -s reload


# Reload 상태 확인
#if [ $? -eq 0 ]; then
#    echo "Nginx reloaded successfully."
#else
#    echo "Nginx reload failed. Restoring backup..."
##    cp "${NGINX_CONFIG_PATH}.bak" "$NGINX_CONFIG_PATH"
##    docker exec "$NGINX_CONTAINER_NAME" nginx -s reload
#    if [ $? -eq 0 ]; then
#        echo "Nginx configuration restored and reloaded successfully."
#    else
#        echo "Failed to reload Nginx even after restoring configuration."
#    fi
#fi