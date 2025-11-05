#!/bin/bash
set -e

APP_DIR="/home/ubuntu/app/monew-server"
CONTAINER_NAME="monew-server"
IMAGE_URI_FILE="$APP_DIR/image_uri.txt"
ENV_FILE="$APP_DIR/scripts/env_vars.sh"

if [ -f "$ENV_FILE" ]; then
  echo "환경 변수 파일 로드: $ENV_FILE"
  source $ENV_FILE
else
  echo "환경 변수 파일($ENV_FILE)을 찾을 수 없습니다."
  exit 1
fi

TOKEN=$(curl -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")

AWS_REGION=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/dynamic/instance-identity/document | jq -r .region)

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
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_PROD_HOST=$DB_PROD_HOST \
  -e DB_PROD_PORT=$DB_PROD_PORT \
  -e DB_PROD_NAME=$DB_PROD_NAME \
  -e DB_PROD_USER=$DB_PROD_USER \
  -e DB_PROD_PASSWORD=$DB_PROD_PASSWORD \
  -e PROD_MONGO_URI=$PROD_MONGO_URI \
  $ECR_IMAGE_URI

echo "배포 완료"