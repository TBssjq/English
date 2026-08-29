pluginManagement {
    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 腾讯云镜像：仅代理 androidx / Google / Android 依赖。
        // 该镜像对部分产物(如 JetBrains Compose) 只同步了元数据、缺少 aar，
        // 而 Gradle 命中元数据后会锁定该仓库取产物且不再回退，故需限制范围。
        maven {
            url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "English"
include(":app")
 