# -*- mode: Python -*-

config.define_string('registry')
cfg = config.parse()

LOCAL_REGISTRY = cfg.get('registry', '')
TILT_SERVICES = ['api-gateway', 'buyer-bff', 'marketplace-service']
ALL_MODULES = [
    'auth-server', 'api-gateway', 'buyer-bff', 'seller-bff',
    'profile-service', 'promotion-service', 'wallet-service',
    'marketplace-service', 'order-service', 'search-service',
    'notification-service', 'loyalty-service', 'activity-service',
    'webhook-service', 'subscription-service', 'buyer-portal',
]

if LOCAL_REGISTRY:
    default_registry(LOCAL_REGISTRY)

k8s_yaml('k8s/namespace.yaml')
k8s_yaml('k8s/infra/base.yaml')
k8s_yaml(kustomize('k8s/apps/overlays/dev'))

def watched_paths(service_name):
    return [
        service_name + '/',
        'shop-common/',
        'shop-contracts/',
        'pom.xml',
        'docker/Dockerfile.module',
    ]

for service_name in TILT_SERVICES:
    docker_build(
        'shop/' + service_name + ':dev',
        context='.',
        dockerfile='docker/Dockerfile.module',
        build_args={'MODULE': service_name},
        only=watched_paths(service_name),
    )

for service_name in ALL_MODULES:
    labels = ['tilt-managed'] if service_name in TILT_SERVICES else ['static']
    port_forwards = ['8080:8080'] if service_name == 'api-gateway' else []
    k8s_resource(service_name, labels=labels, port_forwards=port_forwards)
