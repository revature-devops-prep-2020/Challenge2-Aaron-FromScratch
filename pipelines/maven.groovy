pipeline {
    agent any
    environment {
       projectName = 'challenge2'
       gitBranch = 'dev'
   }
    tools {
        maven 'maven'
    }

    stages {
        stage('Pull') {
            steps {
                git url: "https://github.com/revature-devops-prep-2020/Challenge2-Aaron-FromScratch.git", branch: "dev"
            }
            post {
                failure{
                    slackSend(color: '#FF0000', message: "${projectName}' [${gitBranch}:${currentBuild.number}] has failed to pull project from github.")
                }
            }
        }
        stage('Build') {
            steps {
                dir("maven-app") {
                    sh "mvn package"
                }
            }
            post {
                failure{
                    slackSend(color: '#FF0000', message: "${projectName}' [${gitBranch}:${currentBuild.number}] has failed to build.")
                }
            }
        }
        stage("Sonar") {
            steps {
                dir("maven-app") {
                    withSonarQubeEnv("SonarCloud") {
                        sh "mvn verify sonar:sonar"
                    }
                }
            }
            post {
                failure{
                    slackSend(color: '#FF0000', message: "${projectName}' [${gitBranch}:${currentBuild.number}] has failed to run sonar tests.")
                }
            }
        }
        stage('quality gate'){
            steps{
                dir("maven-app") {
                    // withSonarQubeEnv('SonarCloud')
                    // {
                    //     timeout(time: 10, unit: 'MINUTES') {
                    //         // Parameter indicates whether to set pipeline to UNSTABLE if Quality Gate fails
                    //         // true = set pipeline to UNSTABLE, false = don't
                    //         waitForQualityGate abortPipeline: false
                    //     }
                    // }
                    echo 'pass'
                }
            }
            post {
                failure{
                    slackSend(color: '#FF0000', message: "${projectName}' [${gitBranch}:${currentBuild.number}] did not pass the quality gate")
                }
            }
        }
        stage("Push Docker Image") {
            steps {
                script {
                    dir("maven-app") {
                        docker.withTool("docker") {
                            repoIdCurrent = "aarondownward/maven-app:${currentBuild.number}"
                            repoIdLatest = "aarondownward/maven-app:latest"
                            imageCurrent = docker.build(repoIdCurrent)
                            imageLatest = docker.build(repoIdLatest)
                            docker.withRegistry("https://registry.hub.docker.com", "dockerhub-cred") {
                                imageLatest.push()
                                imageCurrent.push()
                            }
                        }
                    }
                }
            }
            post {
                failure{
                    slackSend(color: '#FF0000', message: "${projectName}' [${gitBranch}:${currentBuild.number}] has failed to push image to docker hub.")
                }
            }
        }
        stage("Deploy to Testing") {
            steps {
                dir("kube-deployments/testing") {
                    withKubeConfig([credentialsId: 'kubectl-creds', serverUrl: 'https://A1DAD4B84EA09429D9D2B23B0E2F826C.gr7.us-west-2.eks.amazonaws.com']) {
                        sh 'kubectl apply -f .'
                        sh 'kubectl rollout restart deployment/simple-app -n build'
                    }
                }
            }
            post {
                success{
                    slackSend(color: '#00FF00', message: "${projectName}' [${gitBranch}:${currentBuild.number}] successfully completed CI/CD pipeline.")
                }
                failure{
                    slackSend(color: '#FF0000', message: "${projectName}' [${gitBranch}:${currentBuild.number}] has failed to deploy to test server.")
                }
            }
        }
    }

}