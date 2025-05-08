# Changelog

## [2.1.0](https://github.com/naviqore/public-transit-service/compare/v2.0.0...v2.1.0) (2025-05-08)


### Features

* add repository to config directly instead of gtfs uri ([8b57cbb](https://github.com/naviqore/public-transit-service/commit/8b57cbbdd39cccb3045a1fa088916e3a22a22881))
* **app:** add support for loading GTFS feed from S3 URI ([284941b](https://github.com/naviqore/public-transit-service/commit/284941b7d26b3d632d6fd2c68bffefba15404139))
* fully automate release pipeline ([4b012c0](https://github.com/naviqore/public-transit-service/commit/4b012c042b84f7255a6db7c2fe61b587fe9f6037))
* introduce a builder pattern for connection query config ([0bf1a1b](https://github.com/naviqore/public-transit-service/commit/0bf1a1b9452a368c2777660575dd1da7a5c8f9e2))
* introduce a builder pattern for service config ([aa1bba5](https://github.com/naviqore/public-transit-service/commit/aa1bba5755994a9a558620b31f980701dd125774))
* move error handling from library to app layer ([a07e9f0](https://github.com/naviqore/public-transit-service/commit/a07e9f0bb3c068085efd0ac6fbb08686751ef843))


### Bug Fixes

* add spring-boot-starter-validation to resolve Bean Validation provider issue ([eb2f930](https://github.com/naviqore/public-transit-service/commit/eb2f930f63000129b498c60a22f70257c1853866))
* correct folder path org.naviqore.app ([207c6f5](https://github.com/naviqore/public-transit-service/commit/207c6f587f474d8f823a2755851d092a53fe9433))


### Documentation

* add a hint to maven central in README ([57a1b54](https://github.com/naviqore/public-transit-service/commit/57a1b543c0cbb9572cf3abb39b38754cb9c95fe4))
* refer to RAPTOR in upper case ([577abff](https://github.com/naviqore/public-transit-service/commit/577abff53bac8e9fa7ba4483177b01f1cc2c2ddf))
* update public transit service example ([8925ddd](https://github.com/naviqore/public-transit-service/commit/8925dddf630f078a777c6787dbd160d16597bdef))

## [2.0.0](https://github.com/naviqore/public-transit-service/compare/v1.2.0...v2.0.0) (2025-04-27)


### ⚠ BREAKING CHANGES

* **mvn:** The structure of the project has been altered significantly.
* **domain:** groupId and all domain references now use .org

### Features

* **api:** add global CORS configuration for public API access ([807f5a2](https://github.com/naviqore/public-transit-service/commit/807f5a2a14769b0091cb53c40ef831d56c7f583c)), closes [#208](https://github.com/naviqore/public-transit-service/issues/208)
* introduce central data provider for GTFS datasets in the gtfs sample module ([a5212a8](https://github.com/naviqore/public-transit-service/commit/a5212a8fd3098801545e844121e3bd5d56a7bb48)), closes [#208](https://github.com/naviqore/public-transit-service/issues/208)


### Bug Fixes

* change visibility of RAPTOR benchmark to public ([c97d0ef](https://github.com/naviqore/public-transit-service/commit/c97d0ef2312e56dea2dcf26b72b4d977b3631a0e)), closes [#208](https://github.com/naviqore/public-transit-service/issues/208)
* **release:** add issue write permissions to GitHub release please ac… ([ffb427c](https://github.com/naviqore/public-transit-service/commit/ffb427c0fd09f5d4bee2e9516bc3b09724dab28a))
* **release:** add issue write permissions to GitHub release please action ([0c14d58](https://github.com/naviqore/public-transit-service/commit/0c14d5841e0876e92477cfb4f0eaa938ea13f970))
* typo in README ([9edaff7](https://github.com/naviqore/public-transit-service/commit/9edaff753854ef33298ebd3ea0d1706b428602c0)), closes [#208](https://github.com/naviqore/public-transit-service/issues/208)


### Documentation

* rework CONTRIBUTING.md and README.md ([5d92a70](https://github.com/naviqore/public-transit-service/commit/5d92a70e17dc00c391bf1efc98b7b7738221e23d)), closes [#208](https://github.com/naviqore/public-transit-service/issues/208)
* rework README.md ([3619415](https://github.com/naviqore/public-transit-service/commit/36194151aadebeee98297738141ee29f809f2573)), closes [#208](https://github.com/naviqore/public-transit-service/issues/208)
* update README ([190f473](https://github.com/naviqore/public-transit-service/commit/190f4739f9110d62066b3bcb27b30235658c0f47)), closes [#208](https://github.com/naviqore/public-transit-service/issues/208)


### Code Refactoring

* **domain:** migrate from .ch to .org for OSS alignment ([d156dae](https://github.com/naviqore/public-transit-service/commit/d156daeaf84025de329b0ee13bf85a5b0a5d4409)), closes [#208](https://github.com/naviqore/public-transit-service/issues/208)
* **mvn:** split project into maven modules ([998a450](https://github.com/naviqore/public-transit-service/commit/998a45025cfa2442ddf88164337c89cd9f7c8b9d)), closes [#208](https://github.com/naviqore/public-transit-service/issues/208)
