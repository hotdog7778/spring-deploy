def upstreamState1 = "unknown"
def upstreamState2 = "unknown"

pipeline {
    agent any

    environment {
        HOST_IP="192.168.219.106"
        // HOST_IP="172.20.0.1"
        HOST_USER="tgkim"
        HOST_PEM="/var/jenkins_home/mac_host_key.pem"

        GIT_BRANCH_NAME = "main"
        GIT_REPO = "https://github.com/hotdog7778/spring-deploy.git"

        NGINX_CONTAINER_NAME="zerodowntimepipeline-tomcat-nginx-1"
        NGINX_CONFIG_PATH="/etc/nginx/nginx.conf"

        TOMCAT_1_CONTAINER_NAME = "zerodowntimepipeline-tomcat-tomcat1-1"
        TOMCAT_1_IP = "172.20.0.101"
        TOMCAT_1_PORT = "8080"
        TOMCAT_1_SHUTDOWN_PORT = "8005"
        TOMCAT_1_SHUTDOWN_CMD = "SHUTDOWN"

        TOMCAT_2_CONTAINER_NAME = "zerodowntimepipeline-tomcat-tomcat2-1"
        TOMCAT_2_IP = "172.20.0.102"
        TOMCAT_2_PORT = "8080"
        TOMCAT_2_SHUTDOWN_PORT = "8005"
        TOMCAT_2_SHUTDOWN_CMD = "SHUTDOWN"

        TOMCAT_1_DEPLOY_PATH = "/Users/tgkim/jenkins/ZeroDowntimePipeline-tomcat/tomcat1/webapp/ROOT.war"
        TOMCAT_1_ARTIFACT_NAME = "ROOT-1.0-SNAPSHOT.war"
        TOMCAT_2_DEPLOY_PATH = "/Users/tgkim/jenkins/ZeroDowntimePipeline-tomcat/tomcat2/webapp/ROOT.war"
        TOMCAT_2_ARTIFACT_NAME = "ROOT-1.0-SNAPSHOT.war"
    }

    tools {
        maven "3.9.9" // Maven Tool Name
    }

    //TODO: 정상실행된 아티팩트는 백업

    stages {

        stage('Build and Deploy Artifact') {
            steps {
                script {
                    echo "🧨🧨🧨 Git Clone 및 Maven 빌드 시작"

                    git branch: "${GIT_BRANCH_NAME}", credentialsId: 'github-tgkim', url: GIT_REPO

                    sh """
                        mvn clean package -DskipTests
                    """
                    echo "✅ Maven 빌드 완료"

                    // WAR 파일 배포
                    def artifactPath1 = "${env.WORKSPACE}/target/${TOMCAT_1_ARTIFACT_NAME}"
                    def artifactPath2 = "${env.WORKSPACE}/target/${TOMCAT_2_ARTIFACT_NAME}"
                    sh """
                        scp -o StrictHostKeyChecking=no -i ${HOST_PEM} ${artifactPath1} ${HOST_USER}@${HOST_IP}:${TOMCAT_1_DEPLOY_PATH}
                        scp -o StrictHostKeyChecking=no -i ${HOST_PEM} ${artifactPath2} ${HOST_USER}@${HOST_IP}:${TOMCAT_2_DEPLOY_PATH}
                    """
                    echo "✅ 아티팩트 배포 완료"
                }
            }
        }

        // 업스트림 상태 초기화
        stage('init upstream state') {
            steps {
                script {
                    upstreamState1 = sh(returnStdout: true, script: """
                        docker exec ${NGINX_CONTAINER_NAME} sh -c "grep '${TOMCAT_1_IP}' '${NGINX_CONFIG_PATH}' | grep 'down' > /dev/null && echo 'down' || echo 'up'"
                    """).trim()
                    upstreamState2 = sh(returnStdout: true, script: """
                        docker exec ${NGINX_CONTAINER_NAME} sh -c "grep '${TOMCAT_2_IP}' '${NGINX_CONFIG_PATH}' | grep 'down' > /dev/null && echo 'down' || echo 'up'"
                    """).trim()

                    echo "✅ Nginx 업스트림: upsetrea-1:${upstreamState1}, Upsetrea-2:${upstreamState2}"
                }
            }
        }

        // 종료단계 시작
        stage('업스트림1 up->down') {
            steps {
                script {
                    // 원하는 상태 아니면 에러
                    if (upstreamState2 == 'down') {
                        error "🧨🧨🧨 업스트림2가 down 상태입니다. 업스트림1을 down 시지키않고 파이프라인 종료"
                    }

                    // Nginx 전환하기
                    // Upstream-1 : up->down
                    sh """
                        docker exec ${NGINX_CONTAINER_NAME} cp -a ${NGINX_CONFIG_PATH} ${NGINX_CONFIG_PATH}.bak
                        docker exec ${NGINX_CONTAINER_NAME} cp -a ${NGINX_CONFIG_PATH} ${NGINX_CONFIG_PATH}.new
                        docker exec ${NGINX_CONTAINER_NAME} /bin/sh -c "sed -i 's|${TOMCAT_1_IP}:${TOMCAT_1_PORT}.*|${TOMCAT_1_IP}:${TOMCAT_1_PORT} down;|g' ${NGINX_CONFIG_PATH}.new"
                        docker exec ${NGINX_CONTAINER_NAME} cp -f ${NGINX_CONFIG_PATH}.new ${NGINX_CONFIG_PATH}
                    """

                    try {
                        // nginx 설정 테스트 및 리로드
                        sh """
                        docker exec ${NGINX_CONTAINER_NAME} nginx -t || exit 1
                        docker exec ${NGINX_CONTAINER_NAME} nginx -s reload
                        """
                        // 업스트림상태 최신화
                        echo "✅ Nginx Upstream 1 Down 완료"
                        upstreamState1 = 'down'
                    } catch (Exception e) {
                        // 백업 파일 복구
                        sh """
                            docker exec ${NGINX_CONTAINER_NAME} cp -f ${NGINX_CONFIG_PATH}.bak ${NGINX_CONFIG_PATH}
                        """
                        error "🧨🧨🧨 Nginx Upstream 전환 실패. nginx설정파일을 원복하고 스크립트 종료.${e.message}"
                    }

                    echo "✅ Nginx 리로드 완료. Upsetrea-1:${upstreamState1}, Upsetrea-2:${upstreamState2}"
                }
            }
        }

        stage('Shutdown Tomcat 1') {
            steps {
                script {
                    echo "🧨🧨🧨 Tomcat 1 셧다운 시작"

                    // Shutdown 명령 전송
                    sh "echo '${TOMCAT_1_SHUTDOWN_CMD}' | nc ${TOMCAT_1_IP} ${TOMCAT_1_SHUTDOWN_PORT}"
                    sleep(time: 10, unit: "SECONDS")

                    // Shutdown 상태 확인
                    def isRunning = sh(
                        script: """
                            docker ps | grep '${TOMCAT_1_CONTAINER_NAME}' || echo 'stopped'
                        """,
                        returnStdout: true
                    ).trim()

                    if (isRunning.contains("stopped")) {
                        echo "✅ Tomcat 1 셧다운 완료"
                    } else {
                        echo "🧨🧨🧨 Tomcat 1이 아직 실행 중입니다. 추가 대기 시작..."

                        // 추가 대기 (두 번째 대기)
                        sleep(time: 20, unit: "SECONDS")
                        // Tomcat 1 상태 확인 (두 번째 확인)
                        def isRunningSecondCheck = sh(
                            script: """
                                docker ps | grep '${TOMCAT_1_CONTAINER_NAME}' || echo 'stopped'
                            """,
                            returnStdout: true
                        ).trim()

                        if (isRunningSecondCheck.contains("stopped")) {
                            echo "✅ Tomcat 1 셧다운 완료 (두 번째 확인)"
                        } else {
                            error "🧨🧨🧨 Tomcat 1 shutdown 실패: 두 번 확인 후에도 실행 중입니다."
                        }
                    }
                }
            }
        }

        stage('Startup Tomcat 1') {
            steps {
                script {
                    echo "🧨🧨🧨 Tomcat 1 시작"

                    // Tomcat 컨테이너 시작
                    sh "docker start ${TOMCAT_1_CONTAINER_NAME}"

                    // Tomcat Health Check
                    sleep(time: 10, unit: "SECONDS") // 초기 대기
                    def isHealthy = false
                    for (int i = 0; i < 6; i++) { // 6번까지 재시도 (최대 60초)
                        def healthCheck = sh(
                            script: """
                                curl -s -o /dev/null -w "%{http_code}" http://${TOMCAT_1_IP}:${TOMCAT_1_PORT}
                            """,
                            returnStdout: true
                        ).trim()
                        if (healthCheck == "200") {
                            echo "✅  ${healthCheck} 톰캣 헬스체크 완료"
                            isHealthy = true
                            break
                        }
                        echo "🧨🧨🧨 Tomcat 1이 아직 준비되지 않았습니다. 5초 대기..."
                        sleep(time: 10, unit: "SECONDS")
                    }

                    if (!isHealthy) {
                        error "🧨🧨🧨 Tomcat 1 Startup 실패: Health Check 통과하지 못함."
                    } else {
                        echo "✅ Tomcat 1 기동 성공"
                    }
                }
            }
        }

        // 업스트림 전환단계 시작
        stage('Upstream1 down->up , Upstream2 up->down') {
            steps {
                script {
                    // 현재 upstream 상태 가져오기. Want State [ 1=down, 2=up ]
                    // 원하는 상태 아니면 에러
                    if(!(upstreamState1 == 'down' && upstreamState2 == 'up')){
                        error "🧨🧨🧨 현재 상태를 확인하세요. upstream-1: ${upstreamState1}, upstream-2: ${upstreamState2}"
                    }

                    // Nginx 전환하기
                    // Upstream-1 : down->up ,
                    // Upstream-2 : up->down
                    sh """
                    docker exec ${NGINX_CONTAINER_NAME} cp -a ${NGINX_CONFIG_PATH} ${NGINX_CONFIG_PATH}.bak
                    docker exec ${NGINX_CONTAINER_NAME} cp -a ${NGINX_CONFIG_PATH} ${NGINX_CONFIG_PATH}.new
                    docker exec ${NGINX_CONTAINER_NAME} /bin/sh -c "sed -i 's|${TOMCAT_1_IP}:${TOMCAT_1_PORT}.*down;|${TOMCAT_1_IP}:${TOMCAT_1_PORT};|g' ${NGINX_CONFIG_PATH}.new"
                    docker exec ${NGINX_CONTAINER_NAME} /bin/sh -c "sed -i 's|${TOMCAT_2_IP}:${TOMCAT_2_PORT}.*|${TOMCAT_2_IP}:${TOMCAT_2_PORT} down;|g' ${NGINX_CONFIG_PATH}.new"
                    docker exec ${NGINX_CONTAINER_NAME} cp -f ${NGINX_CONFIG_PATH}.new ${NGINX_CONFIG_PATH}
                    """

                    try {
                        // nginx 설정 테스트 및 리로드
                        sh """
                        docker exec ${NGINX_CONTAINER_NAME} nginx -t || exit 1
                        docker exec ${NGINX_CONTAINER_NAME} nginx -s reload
                        """
                        // 업스트림상태 최신화
                        echo "✅ 업스트림 변경 완료"
                        upstreamState1 = "up"
                        upstreamState2 = "down"
                    } catch (Exception e) {
                        // 백업 파일 복구
                        sh """
                            docker exec ${NGINX_CONTAINER_NAME} cp -f ${NGINX_CONFIG_PATH}.bak ${NGINX_CONFIG_PATH}
                        """
                        error "🧨🧨🧨 Nginx Upstream 전환 실패. nginx설정파일을 원복하고 스크립트 종료.${e.message}"
                    }

                    echo "✅ Nginx 리로드 완료. Upsetrea-1:${upstreamState1}, Upsetrea-2:${upstreamState2}"
                }
            }
        }

        // 정상화 단계 시작
        stage('Shutdown Tomcat 2') {
            steps {
                script {
                    echo "🧨🧨🧨 Tomcat 2 셧다운 시작"

                    // Nginx 체크
                    if (upstreamState1 != 'up') {
                        // 1번이 up상태가 아니면 2번down을 진행하지 않음
                        echo "🧨🧨🧨 1번이 up상태가 아니면 2번down을 진행하지 않음"
                    }

                    // Shutdown 명령 전송
                    sh "echo '${TOMCAT_2_SHUTDOWN_CMD}' | nc ${TOMCAT_2_IP} ${TOMCAT_2_SHUTDOWN_PORT}"
                    sleep(time: 10, unit: "SECONDS")

                    // Shutdown 상태 확인
                    def isRunning = sh(
                        script: """
                            docker ps | grep '${TOMCAT_2_CONTAINER_NAME}' || echo 'stopped'
                        """,
                        returnStdout: true
                    ).trim()

                    if (isRunning.contains("stopped")) {
                        echo "✅ Tomcat 2 셧다운 완료"
                    } else {
                        echo "🧨🧨🧨 Tomcat 2 아직 실행 중입니다. 추가 대기 시작..."

                        // 추가 대기 (두 번째 대기)
                        sleep(time: 20, unit: "SECONDS")
                        // Tomcat 1 상태 확인 (두 번째 확인)
                        def isRunningSecondCheck = sh(
                            script: """
                                docker ps | grep '${TOMCAT_2_CONTAINER_NAME}' || echo 'stopped'
                            """,
                            returnStdout: true
                        ).trim()

                        if (isRunningSecondCheck.contains("stopped")) {
                            echo "✅ Tomcat 2 셧다운 완료 (두 번째 확인)"
                        } else {
                            error "🧨🧨🧨 Tomcat 2 shutdown 실패: 두 번 확인 후에도 실행 중입니다."
                        }
                    }
                }
            }
        }

        stage('Startup Tomcat 2') {
            steps {
                script {
                    echo "🧨🧨🧨 Tomcat 2 시작"

                    // Tomcat 컨테이너 시작
                    sh "docker start ${TOMCAT_2_CONTAINER_NAME}"

                    // Tomcat Health Check
                    sleep(time: 10, unit: "SECONDS") // 초기 대기
                    def isHealthy = false
                    for (int i = 0; i < 6; i++) { // 6번까지 재시도 (최대 60초)
                        def healthCheck = sh(
                            script: """
                                curl -s -o /dev/null -w "%{http_code}" http://${TOMCAT_2_IP}:${TOMCAT_2_PORT}
                            """,
                            returnStdout: true
                        ).trim()
                        if (healthCheck == "200") {
                            echo "✅  ${healthCheck} 톰캣 헬스체크 완료"
                            isHealthy = true
                            break
                        }
                        echo "🧨🧨🧨 Tomcat 2이 아직 준비되지 않았습니다. 5초 대기..."
                        sleep(time: 10, unit: "SECONDS")
                    }

                    if (!isHealthy) {
                        error "🧨🧨🧨 Tomcat 2 Startup 실패: Health Check 통과하지 못함."
                    } else {
                        echo "✅ Tomcat 2 기동 성공"
                    }

                }
            }
        }

        stage('Upstream2 down->up') {
            steps {
                script {

                    // Want upstream State [ 1=up, 2=down ]
                    // 원하는상태가 아니면 에러
                    if (!(upstreamState1 == 'up' && upstreamState2 == 'down')) {
                        error "🧨🧨🧨 현재 상태를 확인하세요. upstream-1: ${upstreamState1}, upstream-2: ${upstreamState2}"
                    }

                    // Nginx 전환하기
                    // Upstream-2 : down->up

                    sh """
                    docker exec ${NGINX_CONTAINER_NAME} cp -a ${NGINX_CONFIG_PATH} ${NGINX_CONFIG_PATH}.bak
                    docker exec ${NGINX_CONTAINER_NAME} cp -a ${NGINX_CONFIG_PATH} ${NGINX_CONFIG_PATH}.new
                    docker exec ${NGINX_CONTAINER_NAME} /bin/sh -c "sed -i 's|${TOMCAT_2_IP}:${TOMCAT_2_PORT}.*down;|${TOMCAT_2_IP}:${TOMCAT_2_PORT};|g' ${NGINX_CONFIG_PATH}.new"
                    docker exec ${NGINX_CONTAINER_NAME} cp -f ${NGINX_CONFIG_PATH}.new ${NGINX_CONFIG_PATH}
                    """

                    try {
                        // nginx 설정 테스트 및 리로드
                        sh """
                        docker exec ${NGINX_CONTAINER_NAME} nginx -t || exit 1
                        docker exec ${NGINX_CONTAINER_NAME} nginx -s reload
                        """
                        // 업스트림상태 최신화
                        echo "✅ Nginx Upstream 2 UP 완료"
                        upstreamState2 = "up"
                    } catch (Exception e) {
                        // 백업 파일 복구
                        sh """
                            docker exec ${NGINX_CONTAINER_NAME} cp -f ${NGINX_CONFIG_PATH}.bak ${NGINX_CONFIG_PATH}
                        """
                        error "🧨🧨🧨 Nginx Upstream 전환 실패. nginx설정파일을 원복하고 스크립트 종료.${e.message}"
                    }

                    echo "✅ Nginx 리로드 완료. Upsetrea-1:${upstreamState1}, Upsetrea-2:${upstreamState2}"
                }
            }
        }


    }
}
