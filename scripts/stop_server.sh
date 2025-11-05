#!/bin/bash

CONTAINER_NAME="monew-server"

if [ $(docker ps -q -f name=^/${CONTAINER_NAME}$) ]; then
    echo "실행 중인 $CONTAINER_NAME 컨테이너를 중지합니다."
    docker stop ${CONTAINER_NAME}
else
    echo "실행 중인 $CONTAINER_NAME 컨테이너가 없습니다."
fi

if [ $(docker ps -a -q -f name=^/${CONTAINER_NAME}$) ]; then
    echo "기존 $CONTAINER_NAME 컨테이너를 삭제합니다."
    docker rm ${CONTAINER_NAME}
else
    echo "기존 $CONTAINER_NAME 컨테이너가 없습니다."
fi

docker image prune -a -f