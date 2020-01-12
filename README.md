# playground-serverless

Playing around with Kubernetes serverless frameworks (and more)

> :warning: This playground is not fully functional. It contains unresolved issues.

The main idea was to create a cron job triggered function sending multiple messages over NATS. Another function should 
receive these messages and scale down to zero when finished.  
First I tried to play around with Kubeless, but it turned out it does not support scale-to-zero feature at the moment.  
After this I tried OpenFaas which supports this, but it looks here (and in Kubeless) everything should be written as a 
Http handler, and there is no direct NATS triggers working with autoscale (the gateway translates NATS messages to Http requests).

<br/>

### cron-function

A Camel Quarkus based function using Kotlin and Gradle to generate and send multiple messages to a given NATS topic.

* run with `./gradlew quarkusDev` (using `quarkusPluginVersion=1.1.1.Final`)
* build runnable jar with `./gradlew quarkusBuild`
* build native GraalVM image with `./gradlew buildNative`

> Install GraalVM using SDKMAN!:  
> ```bash
> sdk install java 19.2.1-grl  
> export GRAALVM_HOME=/home/zoli/.sdkman/candidates/java/19.2.1-grl   
> $GRAALVM_HOME/bin/gu install native-image  
> ```  

The native Docker image was used with the help of this repo https://github.com/pmlopes/openfaas-quarkus-native-template 

The project runs well in standalone mode, but it throws the exception below whe it is deployed to OpenFaas:
```bash
2020/01/12 15:08:14 stderr: Caused by: org.apache.camel.ResolveEndpointFailedException: Failed to resolve endpoint: nats://nats.openfaas:4222?topic=nats-test due to: There are 1 parameters that couldn't be set on the endpoint. Check the uri if the parameters are spelt correctly and that they are properties of the endpoint. Unknown parameters=[{topic=nats-test}]
```

<br/>

### async-function

A Go based function receiving messages from a given NATS topic.

* download dependencies wit `go get`
* run with `go run handler.go`

<br/>

### Kubernetes

Create a Kubernetes cluster using `k3d` https://github.com/rancher/k3d

* install kubectl (1.17.0)

* install k3d (it takes a couple of minutes)  
  ```bash
  curl https://raw.githubusercontent.com/rancher/k3d/master/install.sh | TAG=v1.4.0 bash
  ```

* create Kubernetes cluster
  ```bash
  k3d create
  export KUBECONFIG="$(k3d get-kubeconfig --name='k3s-default')"
  ```
  or create with published port `k3d create --publish 8080:8080`

* install Helm 
  ```bash
  curl https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3 | bash
  ```

<br/>

### OpenFaas

* install OpenFaas with Helm https://github.com/openfaas/faas-netes/tree/master/chart/openfaas
  ```bash
  helm repo add openfaas https://openfaas.github.io/faas-netes/
  helm repo update
  kubectl apply -f https://raw.githubusercontent.com/openfaas/faas-netes/master/namespaces.yml
  helm upgrade openfaas --install openfaas/openfaas --namespace openfaas --set basic_auth=false --set functionNamespace=openfaas-fn
  ```

* install CLI
  ```bash
  curl -sL https://cli.openfaas.com | sudo sh
  export OPENFAAS_URL=http://127.0.0.1:31112
  ```

  * if installed with auth
    ```bash
      kubectl port-forward svc/gateway -n openfaas 31112:8080 &
      faas-cli login -g $OPENFAAS_URL -u admin -p pass
    ```
* add Docker registry
  ```bash
  kubectl run registry --image=registry:latest --port=5000 --namespace openfaas
  kubectl expose deployment registry --namespace openfaas --type=LoadBalancer --port=5000 --target-port=5000
  ```

* list pods with `kubectl get pods -A` and forward ports for gateway and registry with `kubectl -n openfaas port-forward <pod_name> <port_number> &`

* build and deploy functions
  ```bash
  faas-cli build -f <function>.yml
  docker push localhost:5000/<function>:latest
  faas-cli deploy -f <function>.yml
  ```
  
* see logs with `kubectl logs -f -n openfaas-fn <pod_name>`

* remove function with `faas-cli remove -f <function>.yml`

##### Test NATS

see https://github.com/openfaas-incubator/nats-connector

* install NATS publisher
  ```bash
  faas-cli deploy --name publish-message --image openfaas/publish-message:latest --fprocess='./handler' --env nats_url=nats://nats.openfaas:4222
  faas-cli invoke publish-message <<< "test message"
  ```

* or portforward the NATS pod and publish a message using telnet
  ```bash
  telnet localhost 4222
  pub nats-test 5
  hello
  ```

##### Test auto-scaling

see https://github.com/openfaas/workshop

* install OpenFass with faas-idler dry-run disabled
* or set dry-run=false in faas-idler deployment
  ```bash
  kubectl edit deployments.apps -n openfaas faas-idler
  ```

* create and deploy any function (in /openfaas-test) with the labels below
  ```bash
  faas-cli new --lang=go hello-openfaas
  faas-cli build -f hello-openfaas.yml
  docker tag hello-openfaas:latest localhost:5000/hello-openfaas:latest
  docker push localhost:5000/hello-openfaas:latest
  faas-cli deploy -f hello-openfaas.yml --label "com.openfaas.scale.min=0" --label "com.openfaas.scale.zero=true" --label "faasIdler.inactivityDuration=1m"
  ```
  > update the image in hello-openfaas.yml to localhost:5000/hello-openfaas:latest

* call function using dashboard on http://localhost:8080/ui/
* or by using `faas-cli invoke hello-openfaas`, type message and press Ctrl+d 

The function should scale down after 1 minute of inactivity, and should scale back to 1 when it is invoked.

<br/>

### Kubeless

see https://kubeless.io/docs/quick-start/

in /kubeless

* create Kubernetes cluster and with NGINX ingress
  ```bash
  k3d create --publish 8080:8080
  export KUBECONFIG="$(k3d get-kubeconfig --name='k3s-default')"
  ...
  kubectl create deployment nginx --image=nginx
  kubectl apply -f ingress.yml
  ```
  
* install Kubeless
  ```bash
  export RELEASE=v1.0.5
  kubectl create ns kubeless
  kubectl create -f https://github.com/kubeless/kubeless/releases/download/$RELEASE/kubeless-$RELEASE.yaml
  ```

* install CLI
  ```bash
  export OS=$(uname -s| tr '[:upper:]' '[:lower:]')
  curl -OL https://github.com/kubeless/kubeless/releases/download/$RELEASE/kubeless_$OS-amd64.zip && \
    unzip kubeless_$OS-amd64.zip && \
    sudo mv bundles/kubeless_$OS-amd64/kubeless /usr/local/bin/
  ```

* install NATS operator https://github.com/nats-io/nats-operator
  ```bash
  kubectl apply -f https://raw.githubusercontent.com/nats-io/nats-operator/v0.6.0/deploy/00-prereqs.yaml
  kubectl apply -f https://raw.githubusercontent.com/nats-io/nats-operator/v0.6.0/deploy/10-deployment.yaml
  ```

* create NATS cluster `kubectl apply -f nats-cluster.yml`

* install NATS trigger controller `kubectl apply -f nats-trigger-controller-v1.0.0.yaml`

##### Test Kubeless

* create function
  ```bash
  kubeless function deploy hello --runtime python2.7 --from-file hello-kubeless.py --handler test.hello
  ```

* list functions using `kubectl get function` or `kubeless function ls`

* call function using `kubeless function call hello --data 'Hello world!'`

* delete function using `kubeless delete function hello`

##### Test NATS

* create NATS trigger
  ```bash
  kubeless trigger nats create hello-nats-trigger --function-selector created-by=kubeless,function=hello --trigger-topic test
  ```

* list triggers `kubeless trigger nats list`

* publish NATS message
  ```bash
  kubeless trigger nats publish --url nats://localhost:4222 --topic test --message "Hello World!"
  ```
* delete trigger `kubeless trigger nats delete hello-nats-trigger`
  
<br/>
  
It is possible to create auto-scale with the command below, but it does not scale down to zero
```bash
kubeless autoscale create hello --min 0 --max 2 --metric qps --value 1
```

<br/>

### Clean-up

* delete Kubernetes cluster with `k3d delete`