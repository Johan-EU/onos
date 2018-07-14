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