import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.protobuf) apply false
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.detekt) apply false
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

subprojects {
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    extensions.configure<DetektExtension> {
        parallel = true
        buildUponDefaultConfig = true
        config = files("${rootDir}/detekt.yml")
    }

    dependencies {
        add("detektPlugins", rootProject.libs.detekt.formatting)
    }

    applyDetektFormatting()
}

fun Project.applyDetektFormatting() {
    pluginManager.apply(DetektPlugin::class)

    fun Detekt.configure(enableAutoCorrect: Boolean) {
        description = "Run detekt ktlint wrapper"
        parallel = true
        setSource(files(projectDir))
        config.setFrom(files("$rootDir/detekt-formatting.yml"))
        buildUponDefaultConfig = true
        disableDefaultRuleSets = true
        autoCorrect = enableAutoCorrect
        reports {
            xml {
                required.set(true)
                outputLocation.set(file("$buildDir/reports/detekt/detektFormatting.xml"))
            }
            html.required.set(false)
            txt.required.set(false)
        }
        include(listOf("**/*.kt", "**/*.kts"))
        exclude("build/")
        dependencies {
            add("detektPlugins", rootProject.libs.detekt.formatting)
        }
    }

    tasks.register<Detekt>("ktlintCheck") {
        configure(false)
    }
    tasks.register<Detekt>("ktlintFormat") {
        configure(true)
    }
}