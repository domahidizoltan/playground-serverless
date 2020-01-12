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


!!!!!!!!!!!!!!!! ERROR

<br/>

### async-function

A Go based function receiving messages from a given NATS topic.

<br/>

### OpenFaas

### Kubeless