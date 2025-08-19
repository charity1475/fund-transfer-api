# How to deploy the application
# ============================
## Step 1: Install Argo CD
# To deploy the application, you need to have Argo CD installed in your Kubernetes cluster. Follow these
# steps to install Argo CD:
# Step 1: Install Argo CD
```shell
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo
kubectl port-forward svc/argocd-server -n argocd 8080:443
```
# Step 2: Access the Argo CD UI
# Open your web browser and navigate to `http://localhost:8080`. You can log in using the default credentials:
# Username: `admin`
# Password: The password retrieved from the previous command.
#
# Step 3: Create and Sync the Application
# Now you'll create an Argo CD Application to sync the Kubernetes manifest files from your Git repository.
# Two ways for this: either using argocd.yaml manifest at /k8s/argocd or using ArgoCD UINavigate to the "New App" page:

# Step 3: Create DB resources: for demo credentials have been included in the k8s/db folder.
```shell
kubectl apply -f k8s/db/ -n fund-transfer
```




