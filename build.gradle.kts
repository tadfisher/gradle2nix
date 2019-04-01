plugins {
    base
    idea
}

tasks {
    wrapper {
        gradleVersion = "5.3.1"
        distributionType = Wrapper.DistributionType.ALL
    }
}
