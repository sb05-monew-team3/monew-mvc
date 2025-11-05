#!/bin/bash

APP_DIR="/home/ubuntu/app/monew-server"
CONTAINER_NAME="monew-server"

aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REPOSITORY_URI}

if [ -z "$ECR_IMAGE_URI" ]; then
  echo "ECR_IMAGE_URI 환경 변수가 설정되지 않았습니다."
  exit 1
fi

echo "ECR에서 이미지 PULL: $ECR_IMAGE_URI"
docker pull $ECR_IMAGE_URI

echo "Docker 컨테이너 실행..."
if [ $(docker ps -a -q -f name=^/${CONTAINER_NAME}$) ]; then
    docker stop ${CONTAINER_NAME}
    docker rm ${CONTAINER_NAME}
fi

docker run -d --pull always \
  --name ${CONTAINER_NAME} \
  -p 8080:8080 \
  -e "SPRING_PROFILES_ACTIVE=prod" \
  ${ECR_IMAGE_URI}

echo "배포 완료"