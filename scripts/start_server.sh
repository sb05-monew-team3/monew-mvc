#!/bin/bash

APP_DIR="/home/ubuntu/app/monew-server"
CONTAINER_NAME="monew-server"
IMAGE_URI_FILE="$APP_DIR/image_uri.txt" # <--- 이 파일을 읽을 것입니다.

AWS_REGION=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | jq -r .region)
ECR_HOST=$(aws ecr get-authorization-token --region $AWS_REGION --output text --query 'authorizationData[0].proxyEndpoint')

echo "ECR Host: $ECR_HOST"
echo "AWS Region: $AWS_REGION"

aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_HOST

if [ ! -f "$IMAGE_URI_FILE" ]; then
  echo "이미지 URI 파일($IMAGE_URI_FILE)을 찾을 수 없습니다."
  exit 1
fi
ECR_IMAGE_URI=$(cat $IMAGE_URI_FILE)

if [ -z "$ECR_IMAGE_URI" ]; then
  echo "ECR_IMAGE_URI를 파일에서 읽어오지 못했습니다."
  exit 1
fi

echo "ECR에서 이미지 PULL: $ECR_IMAGE_URI"
docker pull $ECR_IMAGE_URI

echo "Docker 컨테이너 실행..."
if [ $(docker ps -a -q -f name=^/${CONTAINER_NAME}$) ]; then
    docker stop ${CONTAINER_NAME}
    docker rm ${CONTAINER_NAME}
fi

docker run -d \
  --name ${CONTAINER_NAME} \
  -p 8080:8080 \
  -e "SPRING_PROFILES_ACTIVE=prod" \
  ${ECR_IMAGE_URI}

echo "배포 완료"