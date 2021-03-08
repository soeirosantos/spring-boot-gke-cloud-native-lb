# Spring Boot with GKE Container-native Load Balancing

In this tutorial we are going to deploy a Spring Boot application to GKE and expose it using the GKE Container-native load balancing for Ingress. We'll also explore Actuator liveness and readiness health groups and how they integrate with the Container-native load balancing health check.

Let's get started creating a GKE cluster

```bash
export gke_zone=us-central1-a
export gke_cluster=le-cluster
gcloud container clusters create $gke_cluster --num-nodes 3 --zone $gke_zone --enable-ip-alias
```

It's going to take a while. When done run the following to configure your access to the cluster:

```bash
gcloud container clusters get-credentials $gke_cluster --zone $gke_zone
```

You should be able to access your cluster using the `kubectl`. Run this to check everything is fine:

```bash
kubectl cluster-info
```

Now let's inspect the Spring Boot app. Clone this repo:

```bash
git clone https://github.com/soeirosantos/spring-boot-gke-cloud-native-lb.git
```

It's a simple Spring Boot app that exposes data via a REST API and simulates data access
via a mock class (check `src/main/kotlin/com/todevornot/videos/domain/Videos.kt`).

Our goal is to deploy this app to GKE using the [Container-native load balancing](https://cloud.google.com/kubernetes-engine/docs/concepts/container-native-load-balancing) to handle external traffic. We want to understand how the Container-native load balancing interacts with the Kubernetes liveness and readiness probes and how the Spring Boot [probe health indicators](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-kubernetes-probes) can be used to improve the application health check and, perhaps, availability.

Take a look at the class `src/main/kotlin/com/todevornot/videos/check/VideoDataBackendCheck.kt`. This class implements a custom `HealthIndicator` ([more about it](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#writing-custom-healthindicators)) and simulates the verification of the avaiability of a data backend service.

```kotlin
@Component
class VideoDataBackendCheck : HealthIndicator {

    private var isHealthy = true

    override fun health(): Health {
        return if (isHealthy) Health.up().build() else Health.down().build()
    }

    fun down() {
        isHealthy = false
    }
}
```

Notice that we have the method `down()` that will allow us to set the application to an unhealthy state manually.

We need to enable the probe health indicators in the Spring Boot application properties. In our case we are using a Spring profile called `dev`, so this config goes here: `src/main/resources/application-dev.properties`

```
management.health.livenessstate.enabled=true
management.health.readinessstate.enabled=true

# if running locally you also need to add this configuration
# when running in Kubernetes this config is automatically enabled
management.endpoint.health.probes.enabled=true
```

Ok, let's check the Kubernetes config now. Open the file `.kube.yaml` and see how we are configuring the container readiness and liveness probes using the Spring Boot health group endpoints:

```yaml
  readinessProbe:
    httpGet:
      path: /actuator/health/readiness
      port: 8080
    initialDelaySeconds: 15
    timeoutSeconds: 1
    periodSeconds: 5
    failureThreshold: 1
    successThreshold: 1
  livenessProbe:
    httpGet:
      path: /actuator/health/liveness
      port: 8080
    initialDelaySeconds: 40
    timeoutSeconds: 2
    periodSeconds: 5
    failureThreshold: 1
    successThreshold: 1
```

(we are setting a couple of things with the default values but that's intentional to make the available config explicit)

Also, observe the `Service` and `Ingress` config in the `.kube.yaml` file. It will create the [Network Endpoint Groups](https://cloud.google.com/load-balancing/docs/negs) responsible by the *magic* behind the Container-native load balancing mechanism.

Let's run it and check a few things.

First, build the application and the Docker image:
(to run the Docker push command, you'll need to provide your own Docker registry. You won't be able to push to my Docker registry :wink:)

```bash
mvn clean package -DskipTests
docker build . -t soeirosantos/videos-catalog:v1
docker push soeirosantos/videos-catalog:v1
```

Update the image in the `.kube.yaml` file if necessary and apply the K8s manifest:

```bash
kubectl apply -f .kube.yaml
```

Check that the pods are running.

```bash
kubectl get po

NAME                                READY   STATUS    RESTARTS   AGE
videos-catalog-559f4f65c4-c7v8f     1/1     Running   0          2m54s
videos-catalog-559f4f65c4-jtsvz     1/1     Running   0          2m54s
videos-catalog-559f4f65c4-phqkz     1/1     Running   0          2m54s

```

Check the ingress.

```bash
kubectl get ingress
NAME               HOSTS   ADDRESS          PORTS   AGE
videos-catalog     *       34.120.196.250   80      4m27s

```

We are going to use that IP to access our app. It may take a couple of minutes for the IP to show up and become fully available.

Set the IP you get from the previous output.

```bash
INGRESS_IP=34.120.196.250
```

Now query your endpoint and you should get a live response (if you get a `404` response wait more a couple of minutes for the LB become available)

```bash
curl -H "content-type: application/json" -X GET http://$INGRESS_IP/actuator/health/readiness
{"status":"UP"}
```

Check your Ingress annotations. You can see the name of the GCP objects that are behind this Ingress.

```bash
kubectl get ingress videos-catalog -o json | jq .metadata.annotations

{
  "ingress.kubernetes.io/backends": "{\"k8s1-235fca81-default-videos-catalog-80-ffe68e8f\":\"HEALTHY\"}",
  "ingress.kubernetes.io/forwarding-rule": "k8s-fw-default-videos-catalog--235fca81907e683d",
  "ingress.kubernetes.io/target-proxy": "k8s-tp-default-videos-catalog--235fca81907e683d",
  "ingress.kubernetes.io/url-map": "k8s-um-default-videos-catalog--235fca81907e683d",
  "kubectl.kubernetes.io/last-applied-configuration": "{\"apiVersion\":\"networking.k8s.io/v1beta1\",\"kind\":\"Ingress\",\"metadata\":{\"annotations\":{\"name\":\"videos-catalog\",\"namespace\":\"default\"},\"spec\":{\"backend\":{\"serviceName\":\"videos-catalog\",\"servicePort\":80}}}\n"
}
```

Assign the name of your backend service (defined in the annotation `ingress.kubernetes.io/backends`) to a variable

```bash
BACKEND_SERVICE=k8s1-235fca81-default-videos-catalog-80-ffe68e8f
```

Let's check the details of the GCP objects behind the Ingress.
(Just a heads up that, from a user perspective, it's not necessary to know the details of the services backing your Kubernetes objects but it's always good to understand what's behind the scenes, isn't it?)

Let's start with the backend.

```bash
gcloud compute backend-services describe $BACKEND_SERVICE --global
# omitting output
```

Take a look at the health check configured for this backend:

```bash
gcloud compute health-checks describe $BACKEND_SERVICE --global

checkIntervalSec: 3
creationTimestamp: '2020-08-07T17:58:22.574-07:00'
description: Kubernetes L7 health check generated with readiness probe settings.
healthyThreshold: 1
httpHealthCheck:
  portSpecification: USE_SERVING_PORT
  proxyHeader: NONE
  requestPath: /actuator/health/readiness
id: '7790891765958985281'
kind: compute#healthCheck
name: k8s1-235fca81-default-videos-catalog-80-ffe68e8f
selfLink: https://www.googleapis.com/compute/v1/projects/gcp-project/global/healthChecks/k8s1-235fca81-default-videos-catalog-80-ffe68e8f
timeoutSec: 2
type: HTTP
unhealthyThreshold: 2

```

As you can see, it's using the readiness probe path related to the port configured for our container and exposed by the k8s Service: `  requestPath: /actuator/health/readiness`. Take a look [in this table](https://cloud.google.com/kubernetes-engine/docs/concepts/ingress#def_inf_hc) to see how the health check configuration is inferred from the readiness probe parameters.

:warning: It's essential to understand that when the backend service health check parameters are inferred from a serving Pod's readiness probe, GKE does not keep the readiness probe and health check synchronized. We may consider using a [BackendConfig](https://cloud.google.com/kubernetes-engine/docs/concepts/ingress#direct_hc_instead) to have more granular control over the backend service configuration, which includes health checks and other [ingress features](https://cloud.google.com/kubernetes-engine/docs/how-to/ingress-features#configuring_ingress_features_through_backendconfig_parameters).

Very well... let's play with our application health check a bit.

First, let's attach our custom health indicator to the readiness health group in the file `application-dev.properties`

```
management.endpoint.health.group.readiness.include=videoDataBackendCheck
```

Build the app, publish the image and apply the changes.

```bash
mvn clean package -DskipTests
docker build . -t soeirosantos/videos-catalog:v2
docker push soeirosantos/videos-catalog:v2
# change the image first
kubectl apply -f .kube.yaml
```

Wait for you change to propagate and pods become available and run the following curl command using the `/down` path:

```
curl -I -X GET http://$INGRESS_IP/down
HTTP/1.1 200
```

It will set the health indicator to false simulating a failing instance.

Note that one pod has changed the state to not-ready:

```bash
kubectl get po

NAME                                READY   STATUS    RESTARTS   AGE
videos-catalog-559f4f65c4-c7v8f     1/1     Running   0          3m
videos-catalog-559f4f65c4-jtsvz     1/1     Running   0          3m
videos-catalog-559f4f65c4-phqkz     0/1     Running   0          3m
```

You can, of course, still access the application because there are two other pods live. Check:

```bash
curl -H "content-type: application/json" -X GET http://$INGRESS_IP/actuator/health/readiness

{"status":"UP"}
```

Verify the GCP health check for all the pods and see that one is failing

```bash
gcloud compute backend-services get-health $BACKEND_SERVICE --global

# omitting output
```

In practice, it would mean that if a feature is failing and it compromises your application from serving requests you should take that instance out of the load balancing by setting its health to down.


Run the curl command pointing to the `/down` path twice more and check that the application becomes unavailable since there are no more health pods to serve requests. To bring the application up you can delete the pods and the replica set will create new ones.

```bash
# you must change the name of the pod to your pod name
kubectl delete po videos-catalog-559f4f65c4-c7v8f
```
Repeat this command for the other pods.

Alternatively, you can use the following command to remove all the pods at once and bring others up.

```bash
kubectl scale deployment videos-catalog --replicas 0
kubectl scale deployment videos-catalog --replicas 3
```

We don't need to manually restart pods to recover their state. We can let Kubernetes restart the failing containers inside the pod if the application gets unhealthy. It's sometimes useful to release objects in a bad state, refresh the configuration, etc. - it may help to recover your application. In the worst case, if the issue doesn't get fixed, your pod will start a `CrashLoopBackoff`. This is for what the liveness probe is used. Let's attach our health indicator to the liveness health group in the file `application-dev.properties`

```
management.endpoint.health.group.liveness.include=videoDataBackendCheck
```

Build the app, publish the image and apply the changes.

```bash
mvn clean package -DskipTests
docker build . -t soeirosantos/videos-catalog:v3
docker push soeirosantos/videos-catalog:v3
# change the image first
kubectl apply -f .kube.yaml
```

Now you can repeat the same tests with the `/down` path. Observe that the pods get restarted automatically.

That's it. I hope you have enjoyed the trip. Feel free to provide comments and feedback on the [twitter thread](https://twitter.com/soeiro_santos/status/1294679648014671872).
