COMPILE_DEPS = CORE_DEPS + [
    "@minimal_json//jar",
    "//drivers/default:onos-drivers-default",
    "//protocols/p4runtime/model:onos-protocols-p4runtime-model",
    "//protocols/p4runtime/api:onos-protocols-p4runtime-api",
]

BUNDLES = [
    "//pipelines/simplebng:onos-pipelines-simplebng",
]

osgi_jar(
    deps = COMPILE_DEPS,
)

onos_app(
    app_name = "org.onosproject.pipelines.simplebng",
    category = "Pipeline",
    description = "Provides pipelines with basic L2/L3 forwarding capabilities and packet-in/out " +
                  "support. Added simple BNG functions.",
    included_bundles = BUNDLES,
    required_apps = [
        "org.onosproject.drivers.p4runtime",
    ],
    title = "Simple BNG",
    url = "https://github.com/Johan-EU/sd-access",
)
