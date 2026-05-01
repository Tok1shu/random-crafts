# Random Crafts

### Versions
[1.21.1](https://github.com/Weyne1/random-crafts/tree/1.21.1)

## Updating / Downgrading Versions
Change the settings in `gradle.properties`
* `minecraft_version` in `gradle.properties`
* Select the correct mc_pack_format from the website https://minecraft.wiki/w/Pack_format
* Update the validation ranges if they are not listed (`build.gradle:8`)
* Synchronize dependencies in `gradle.properties`

## Gradle commands
Build: `./gradlew build`  
Build for a specific platform:
* Fabric: `./gradlew :fabric:build`
* Forge:  `./gradlew :neoforge:build`

Force a build config check without building:  
`./gradlew validateBuildConfig`