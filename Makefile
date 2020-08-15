# make build version=v5
build:
	mvn clean package -DskipTests
	docker build . -t soeirosantos/videos-catalog:$(version)
	docker push soeirosantos/videos-catalog:$(version)

# make set-image version=v5
set-image:
	kubectl set image deployment/videos-catalog videos-catalog=soeirosantos/videos-catalog:$(version)

apply:
	kubectl apply -f .kube.yaml

port-forward:
	kubectl port-forward $$(kubectl get po -l app=videos-catalog -o jsonpath="{.items[0].metadata.name}") 8081:8080
