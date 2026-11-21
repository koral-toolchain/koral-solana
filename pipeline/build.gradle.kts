plugins {
    kotlin("jvm")
    `java-library`
}
dependencies {
    implementation(project(":language"))
    implementation(project(":compiler"))
    implementation(project(":toolchain"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
