// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

//Create Folders

folder(projectFolderName + '/Environment') {
    description('Contains environment jobs')
}

folder(projectFolderName + '/Environment/Instance') {
    description('Contains environment jobs')
}

folder(projectFolderName + '/Environment/Database') {
    description('Contains environment jobs')
}

folder(projectFolderName + '/Environment/Image_Repository') {
    description('Contains environment jobs')
}

// Jobs

def createDBJob = freeStyleJob(projectFolderName + "/Environment/Database/Create_Database")
def createServerJob = freeStyleJob(projectFolderName + "/Environment/Instance/Create_Server")
def deleteDBJob = freeStyleJob(projectFolderName + "/Environment/Database/Delete_Database")
def deleteServerJob = freeStyleJob(projectFolderName + "/Environment/Instance/Delete_Server")
def tagServerJob = freeStyleJob(projectFolderName + "/Environment/Instance/Tag_Server")
def assignElasticIPJob = freeStyleJob(projectFolderName + "/Environment/Instance/Assign_Elastic_IP")
def createImageRepository = freeStyleJob(projectFolderName + "/Environment/Image_Repository/Create_ECR_Repository")
def deleteImageRepository = freeStyleJob(projectFolderName + "/Environment/Image_Repository/Delete_ECR_Repository")
def createDockerMachineJob = freeStyleJob(projectFolderName + "/Environment/Instance/Create_Docker_Server")

// Job configurations

createDBJob.with {
    description('''Create database tables''')

    parameters {
        choiceParam('DATABASE_TYPE', ['Dynamo'], 'Choose your database category')
        stringParam('TABLE_COUNT', '2', 'Enter the number of tables to be created')
        stringParam('TABLE_NAMES', 'TABLE_1,TABLE_2', 'Enter the table names separated by commas')
        stringParam('DYNAMO_TABLE_KEYS', 'USERID;NAME;S;S,ACCENTURE_ID;None;S;None', 'Enter the comma separated partition and sort keys in the format PARTITION_KEY;SORT_KEY;PARTITION_KEY_TYPE;SORT_KEY_TYPE - Enter None if there is no value for a field. KEY_TYPE=S N B')
        stringParam('READ_CAPACITY', '2', 'Enter read capacity')
        stringParam('WRITE_CAPACITY', '2', 'Enter write capacity')
        choiceParam('AWS_DEFAULT_REGION', ['us-west-2', 'us-west-1', 'eu-west-1', 'us-east-1'], 'Choose an AWS region to deploy to')
        choiceParam('SEED_DATA', ['false', 'true'], 'Choose whether or not seed data should be added to the tables')
        stringParam('SEED_DATA_FILE_ENDPOINT', '', 'Enter URL of the seed data file. For example, S3 URL')
    }
    label("aws")
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
    }

    wrappers {
        preBuildCleanup()
        credentialsBinding {
            usernamePassword('AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY', 'AWS_ENVKEY_' + projectFolderName.replaceAll("[^a-zA-Z0-9]+","_"))
            sshAgent('adop-jenkins-master')
        }
    }

    steps {
        shell('''#!/bin/bash
                |set +x
                |
                |#Parameter validation
                |
                |#Make sure the number of tables and number of keys match
                |IFS=',' read -ra KEYS <<< "$DYNAMO_TABLE_KEYS"
                |IFS=',' read -ra TABLES <<< "$TABLE_NAMES"
                |
                |if [ ${#TABLES[@]} != ${TABLE_COUNT} ]; then
                |   echo "number of tables specified does not match the table count"
                |   exit 1
                |fi
                |
                |if [ ${#KEYS[@]} -ne ${#TABLES[@]} ]; then
                |   echo "number of tables not equal to the number of keys specified"
                |   exit 1
                |fi
                |
                |set -x'''.stripMargin())
    }

    steps {
        shell('''#!/bin/bash
                |set +x
                |
                |# Table creation
                |
                |IFS=',' read -ra KEYS <<< "$DYNAMO_TABLE_KEYS"
                |IFS=',' read -ra TABLES <<< "$TABLE_NAMES"
                |
                |for i in "${!TABLES[@]}"; do
                |   IFS=';' read -ra key_pair <<< ${KEYS[i]}
                |
                |   if [ ${#key_pair[@]} -ne 4 ]; then
                |      echo "you must enter attribute/key name and type separated by ;"
                |      exit 1
                |   fi
                |
                |   if [ ${key_pair[1]} == None ]; then
                |       aws dynamodb create-table --table-name ${TABLES[i]} --attribute-definitions AttributeName=${key_pair[0]},AttributeType=${key_pair[2]} --key-schema AttributeName=${key_pair[0]},KeyType=HASH --provisioned-throughput ReadCapacityUnits=${READ_CAPACITY},WriteCapacityUnits=${WRITE_CAPACITY}
                |   else
                |       aws dynamodb create-table --table-name ${TABLES[i]} --attribute-definitions AttributeName=${key_pair[0]},AttributeType=${key_pair[2]} AttributeName=${key_pair[1]},AttributeType=${key_pair[3]} --key-schema AttributeName=${key_pair[0]},KeyType=HASH AttributeName=${key_pair[1]},KeyType=RANGE --provisioned-throughput ReadCapacityUnits=${READ_CAPACITY},WriteCapacityUnits=${WRITE_CAPACITY}
                |   fi
                |done
                |
                |set -x'''.stripMargin())
    }

    steps {
        shell('''#!/bin/bash
                 |set +x
                 |
                 |if [[ "$SEED_DATA" == "true" ]]; then
                 |
                 |     rm -f items.json 
                 |     curl ${SEED_DATA_FILE_ENDPOINT} -o items.json
                 |     sleep 20
                 |     aws dynamodb batch-write-item --request-items file://items.json
                 |
                 |else
                 |
                 |     echo "NO SEED DATA TO BE ADDED"
                 |
                 |fi
                 |
                 |set -x'''.stripMargin())
    }
}

deleteDBJob.with {
    description('''Delete a set of database tables''')
    parameters {
        choiceParam('DATABASE_TYPE', ['Dynamo'], 'Choose your database category')
        stringParam('TABLE_COUNT', '2', 'Enter the number of tables to be deleted')
        stringParam('TABLE_NAMES', 'TABLE_1,TABLE_2', 'Enter the table names separated by commas')
        choiceParam('AWS_DEFAULT_REGION', ['us-west-2', 'us-west-1', 'eu-west-1', 'us-east-1'], 'Choose an AWS region')
    }
    label("aws")
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
    }

    wrappers {
        preBuildCleanup()
        credentialsBinding {
            usernamePassword('AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY', 'AWS_ENVKEY_' + projectFolderName.replaceAll("[^a-zA-Z0-9]+","_"))
            sshAgent('adop-jenkins-master')
        }
    }

    steps {
        shell('''#!/bin/bash
                |set +x
                |
                |# Parameter validation
                |
                |IFS=',' read -ra TABLES <<< "$TABLE_NAMES"
                |
                |if [ ${#TABLES[@]} != ${TABLE_COUNT} ]; then
                |   echo "number of tables specified does not match the table count"
                |   exit 1
                |fi
                |
                |# Table deletion
                |
                |for i in "${!TABLES[@]}"; do
                |   aws dynamodb delete-table --table-name ${TABLES[i]}
                |done
                |
                |set -x'''.stripMargin())
    }

}

createServerJob.with {
	description('''Create an AWS EC2 server with docker installed''')
	parameters {
		stringParam('IMAGE_ID', 'ami-e2a5ac9b', 'Enter the AMI ID for a server with docker installed / configured')
		stringParam('SUBNET_ID', 'subnet-1ad7e77d', 'Enter subnet ID')
		stringParam('SERVER_COUNT', '1', 'Enter number of servers to be provisioned')
		choiceParam('INSTANCE_TYPE', ['t2.micro', 't2.small', 't2.medium', 't2.large'], 'Select an instance type')
		stringParam('SECURITY_GROUP_COUNT', '1', 'Enter the number of security groups')
		stringParam('SECURITY_GROUPS', 'sg-819a9bfa', 'Enter security groups in comma separated format - SG-1,SG-2,...SG-N')
		choiceParam('AWS_DEFAULT_REGION', ['us-west-2', 'us-west-1', 'eu-west-1', 'us-east-1'], 'Choose an AWS region to deploy to')
		stringParam('KEY_PAIR', 'ls-devops', 'Enter SSH key pair')
	}
	label("aws")
	environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
    }

    wrappers {
        preBuildCleanup()
        credentialsBinding {
            usernamePassword('AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY', 'AWS_ENVKEY_' + projectFolderName.replaceAll("[^a-zA-Z0-9]+","_"))
            sshAgent('adop-jenkins-master')
        }
    }

    steps {
    	shell('''#!/bin/bash
    		    |set +x
    		    |
    		    |# Parameter validation
    		    |
    		    |IFS=',' read -ra SECURITY_GROUPS_ARR <<< "$SECURITY_GROUPS"
    		    |
    		    |if [ ${#SECURITY_GROUPS_ARR[@]} != ${SECURITY_GROUP_COUNT} ]; then
    		    |	echo "number of security groups specified does not match the group count"
    		    |fi
    		    |
    		    |# Server provisioning
    		    |
    		    |aws ec2 run-instances --image-id ${IMAGE_ID} --count ${SERVER_COUNT} --instance-type ${INSTANCE_TYPE} --key-name ${KEY_PAIR} --subnet-id ${SUBNET_ID} --security-group-ids ${SECURITY_GROUPS} --region ${AWS_DEFAULT_REGION}
    		    |
    		    |set -x'''.stripMargin())
    }

}

tagServerJob.with {
	description('''Tag server with a name for easy identification on the console''')
	parameters {
		stringParam('INSTANCE_ID', 'i-xxxx', 'Enter the target instance ID')
		stringParam('NAME', 'adop-ec2', 'Replace the default name / tag if required')
		choiceParam('AWS_DEFAULT_REGION', ['us-west-2', 'us-west-1', 'eu-west-1', 'us-east-1'], 'Choose an AWS region')
	}
	label("aws")
	environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
    }

    wrappers {
        preBuildCleanup()
        credentialsBinding {
            usernamePassword('AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY', 'AWS_ENVKEY_' + projectFolderName.replaceAll("[^a-zA-Z0-9]+","_"))
            sshAgent('adop-jenkins-master')
        }
    }

    steps {
    	shell('''#!/bin/bash
    		    |set +x
    		    |
    		    |aws ec2 create-tags --resources ${INSTANCE_ID} --tags Key=Name,Value=${NAME} --region ${AWS_DEFAULT_REGION}
    		    |
    		    |set -x'''.stripMargin())
    }

}

assignElasticIPJob.with {
	description('''Assign an elastic IP to an ec2 server''')
	parameters {
		stringParam('INSTANCE_ID', 'i-xxxx', 'Enter the target instance ID')
		stringParam('ELASTIC_IP', '34.210.251.56', 'Enter elastic IP address')
		choiceParam('AWS_DEFAULT_REGION', ['us-west-2', 'us-west-1', 'eu-west-1', 'us-east-1'], 'Choose an AWS region')
	}
	label("aws")
	environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
    }

    wrappers {
        preBuildCleanup()
        credentialsBinding {
            usernamePassword('AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY', 'AWS_ENVKEY_' + projectFolderName.replaceAll("[^a-zA-Z0-9]+","_"))
            sshAgent('adop-jenkins-master')
        }
    }

    steps {
    	shell('''#!/bin/bash
    			|set +x
    			|
    			|aws ec2 associate-address --public-ip ${ELASTIC_IP} --instance-id ${INSTANCE_ID} --region ${AWS_DEFAULT_REGION} 
    			|
    		    |set -x'''.stripMargin())
    }

}

deleteServerJob.with {
	description('''Delete / terminate server''')
	parameters {
		stringParam('INSTANCE_ID', 'i-xxxx', 'Enter the target instance ID')
		choiceParam('AWS_DEFAULT_REGION', ['us-west-2', 'us-west-1', 'eu-west-1', 'us-east-1'], 'Choose an AWS region')
	}
	label("aws")
	environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
    }

    wrappers {
        preBuildCleanup()
        credentialsBinding {
            usernamePassword('AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY', 'AWS_ENVKEY_' + projectFolderName.replaceAll("[^a-zA-Z0-9]+","_"))
            sshAgent('adop-jenkins-master')
        }
    }

    steps {
    	shell('''#!/bin/bash
    			|set +x
    			|
    			|aws ec2 terminate-instances --instance-ids ${INSTANCE_ID} --region ${AWS_DEFAULT_REGION}
    			|
    			|set -x'''.stripMargin())
    }

}

createImageRepository.with {
    description('''Create ECR image repository''')
    parameters {
        stringParam('IMAGE_NAME', 'adop-hapijs-service', 'Enter image name')
        choiceParam('AWS_DEFAULT_REGION', ['us-west-2', 'us-west-1', 'eu-west-1', 'us-east-1'], 'Choose an AWS region')
    }
    label("aws")
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
    }

    wrappers {
        preBuildCleanup()
        credentialsBinding {
            usernamePassword('AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY', 'AWS_ENVKEY_' + projectFolderName.replaceAll("[^a-zA-Z0-9]+","_"))
            sshAgent('adop-jenkins-master')
        }
    }

    steps {
        shell('''#!/bin/bash
                |set +x
                |
                |aws ecr create-repository --repository-name ${IMAGE_NAME} --region ${AWS_DEFAULT_REGION} 
                |
                |set -x'''.stripMargin())
    }

}

deleteImageRepository.with {
    description('''Delete ECR image repository''')
    parameters {
        stringParam('IMAGE_NAME', 'adop-hapijs-service', 'Enter image name')
        choiceParam('AWS_DEFAULT_REGION', ['us-west-2', 'us-west-1', 'eu-west-1', 'us-east-1'], 'Choose an AWS region')
    }
    label("aws")
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
    }

    wrappers {
        preBuildCleanup()
        credentialsBinding {
            usernamePassword('AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY', 'AWS_ENVKEY_' + projectFolderName.replaceAll("[^a-zA-Z0-9]+","_"))
            sshAgent('adop-jenkins-master')
        }
    }

    steps {
        shell('''#!/bin/bash
                |set +x
                |
                |aws ecr delete-repository --force --repository-name ${IMAGE_NAME} --region ${AWS_DEFAULT_REGION}
                |
                |set -x'''.stripMargin())
    }

}

createDockerMachineJob.with {
    description('''Provision an EC2 with docker installed using docker-machine''')
    parameters {
        stringParam('VPC_ID', 'vpc-d2f611b4', 'Enter VPC ID of server')
        choiceParam('INSTANCE_TYPE', ['t2.micro', 't2.small', 't2.medium', 't2.large'], 'Select an instance type')
        stringParam('SSH_USER', 'ubuntu', 'Enter SSH user name')
        stringParam('SERVER_NAME', 'adop-ec2', 'Enter server name')
        stringParam('ENV_NAME', 'DEV', 'Enter unique environment name')
        choiceParam('AWS_DEFAULT_REGION', ['us-west-2', 'us-west-1', 'eu-west-1', 'us-east-1'], 'Choose an AWS region')
        choiceParam('ZONE', ['a', 'b', 'c'], 'Select availability zone')
        stringParam('SUBNET_ID', 'subnet-1ad7e77d', 'Enter subnet ID')
    }
    label("aws")
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
    }

    wrappers {
        preBuildCleanup()
        credentialsBinding {
            usernamePassword('AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY', 'AWS_ENVKEY_' + projectFolderName.replaceAll("[^a-zA-Z0-9]+","_"))
            sshAgent('adop-jenkins-master')
        }
    }

    steps {
        shell('''#!/bin/bash
                |set +x
                |
                |rm -rf ${WORKSPACE}/${ENV_NAME}
                |mkdir -p ${WORKSPACE}/${ENV_NAME}
                |
                |rm -f ${WORKSPACE}/${ENV_NAME}/docker-machine
                |curl -L https://github.com/docker/machine/releases/download/v0.10.0/docker-machine-`uname -s`-` uname -m` > ${WORKSPACE}/${ENV_NAME}/docker-machine
                |
                |chmod +x ${WORKSPACE}/${ENV_NAME}/docker-machine
                |
                |mkdir ${WORKSPACE}/${ENV_NAME}/ssl
                |
                |${WORKSPACE}/${ENV_NAME}/docker-machine -s ${WORKSPACE}/${ENV_NAME}/ssl create --driver amazonec2 --amazonec2-access-key ${AWS_ACCESS_KEY_ID} --amazonec2-secret-key ${AWS_SECRET_ACCESS_KEY} --amazonec2-vpc-id ${VPC_ID} --amazonec2-instance-type ${INSTANCE_TYPE} --amazonec2-region ${AWS_DEFAULT_REGION} --amazonec2-subnet-id ${SUBNET_ID} --amazonec2-ssh-user ${SSH_USER} --amazonec2-zone ${ZONE} ${SERVER_NAME}
                |
                |set -x'''.stripMargin())
    }

}