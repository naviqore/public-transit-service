# Changelog

## [4.0.0](https://github.com/naviqore/public-transit-service/compare/v3.0.0...v4.0.0) (2026-02-27)


### ⚠ BREAKING CHANGES

* **service:** ScheduleInformationService.getStopTimes now requires a StopScope parameter.
* **service:** ScheduleInformationService.getStopTimes now requires a StopScope parameter.

### Features

* **app:** add time window duration parameter on routing controller ([515a463](https://github.com/naviqore/public-transit-service/commit/515a463e9c9753ced374dd1cc29ecd3b686d0d3f)), closes [#441](https://github.com/naviqore/public-transit-service/issues/441)
* **app:** improve home page with modern layout ([784010e](https://github.com/naviqore/public-transit-service/commit/784010ec6f0e892c3fbb7afe27b3dbcc4ebe6a65))
* **app:** rename navigation items ([ef49c3f](https://github.com/naviqore/public-transit-service/commit/ef49c3f14eb0400171009dda658937369c016b0b))
* **service:** first running attempt of time window connection routing ([b7f3461](https://github.com/naviqore/public-transit-service/commit/b7f34617724a6ef4464067be5d587ef7f3e92be5)), closes [#441](https://github.com/naviqore/public-transit-service/issues/441)
* **service:** first try with time window routing ([02a4cbb](https://github.com/naviqore/public-transit-service/commit/02a4cbb6b9862e88aaf2f0dcfc816743658a5c96)), closes [#441](https://github.com/naviqore/public-transit-service/issues/441)
* **service:** first try with time window routing on isolines ([77057c3](https://github.com/naviqore/public-transit-service/commit/77057c307383d89d11f26e3e2621d03893c97706))
* **service:** implement stop scope resolution for stop events ([a2ab50c](https://github.com/naviqore/public-transit-service/commit/a2ab50c1e2219afe0f5fe344dd1bdc4fe0f879a0)), closes [#441](https://github.com/naviqore/public-transit-service/issues/441)
* **service:** implement stop scope resolution for stop events ([ba89ff1](https://github.com/naviqore/public-transit-service/commit/ba89ff1ade9f4695cdd61f0ebae1f76fc38b7df4))


### Bug Fixes

* **service:** Do not translate maxTravelDuration from service to raptor as they do not relate well. ([86c082f](https://github.com/naviqore/public-transit-service/commit/86c082fadf1b00578a13c40921f1bf864772e42d))
* **service:** fix isBelowMaximumTravelTime always being true ([a766271](https://github.com/naviqore/public-transit-service/commit/a7662713913b042442ef04a993116571e48ce40a))
* **service:** Minor fix to Isoline implementation removing departure/arrival times outside of timewindow (if timewindow is set) ([653ad39](https://github.com/naviqore/public-transit-service/commit/653ad39f8f287d061b104590aef775c7de8c69bb))
* **service:** remove connections that start after requested time window ([0a6bc9f](https://github.com/naviqore/public-transit-service/commit/0a6bc9fb167dcd7fa7b07b4b76ffe88fb05118b8))
* **service:** Remove duplicated connections from timewindow connection queries. ([bc88599](https://github.com/naviqore/public-transit-service/commit/bc88599b6434dd37a92b6fb027528cac066e53bd))


### Documentation

* **app:** remove todo, keep from and to logic for schedule stop time requests ([bff3ab7](https://github.com/naviqore/public-transit-service/commit/bff3ab7eea3d31525d002bc151a83ab23b14bcdf))

## [3.0.0](https://github.com/naviqore/public-transit-service/compare/v2.3.0...v3.0.0) (2026-02-08)


### ⚠ BREAKING CHANGES

* The REST API changes and will break clients.
* **app:** API endpoints now require and return date-time strings with timezone offsets.
* **service:** Service interfaces now use `OffsetDateTime`. `getNextDepartures` is renamed to `getStopTimes`. `getNearestStops` signature is modified.
* **raptor:** RaptorAlgorithm interface now uses OffsetDateTime instead of LocalDateTime.
* **gtfs:** Public interface of the GtfsSchedule and model classes now require OffsetDateTime instead of LocalDateTime. GtfsSchedule has replaced getNextDepartures with getStopTimes.
* **app:** API Error response schema change.

### Features

* **app:** migrate REST API to OffsetDateTime ([eb80037](https://github.com/naviqore/public-transit-service/commit/eb800373782bd2b4621965b5d014df3cb0a51701)), closes [#422](https://github.com/naviqore/public-transit-service/issues/422)
* **app:** standardize api level errors and separate validation logic in controller ([936befd](https://github.com/naviqore/public-transit-service/commit/936befdb37c91221955f98c292f3e2ddd4be775d)), closes [#174](https://github.com/naviqore/public-transit-service/issues/174)
* **gtfs:** migrate to OffsetDateTime and implement DST-safe stop time requests ([eb46bf6](https://github.com/naviqore/public-transit-service/commit/eb46bf62f3c0d69f5afe71073376c7d179f7f609)), closes [#422](https://github.com/naviqore/public-transit-service/issues/422)
* **gtfs:** update link to timetable 2026 for switzerland ([e74a69c](https://github.com/naviqore/public-transit-service/commit/e74a69c4458b87933aa9d8c490028f0c774ad0d1))
* move validation handling to service package in app ([b32ccd4](https://github.com/naviqore/public-transit-service/commit/b32ccd49b34f7fed06ddf55e8fa1c3b051c4eec5))
* **raptor:** apply UTC offsets in route scanning and post-processing ([2c6ada5](https://github.com/naviqore/public-transit-service/commit/2c6ada5eb9013ba8de89ac2e99de4e5e28836d64)), closes [#422](https://github.com/naviqore/public-transit-service/issues/422)
* **raptor:** migrate RAPTOR router to OffsetDateTime for timezone-awareness ([42e8226](https://github.com/naviqore/public-transit-service/commit/42e8226289baff4969e3dcff1ce25361e2ad4635)), closes [#422](https://github.com/naviqore/public-transit-service/issues/422)
* **raptor:** return local agency timezones instead of UTC ([88480c4](https://github.com/naviqore/public-transit-service/commit/88480c472f9a006efa7fd61de48023d1d3f04e23)), closes [#422](https://github.com/naviqore/public-transit-service/issues/422)
* **service:** migrate to OffsetDateTime and update APIs ([c7af86f](https://github.com/naviqore/public-transit-service/commit/c7af86fbb38be999452cbed64243e65daab82f26)), closes [#422](https://github.com/naviqore/public-transit-service/issues/422)
* **service:** sort connection and isoline query results ([0f5d38a](https://github.com/naviqore/public-transit-service/commit/0f5d38ab195fee9aac8d8354a561aa4ae1506cfb)), closes [#422](https://github.com/naviqore/public-transit-service/issues/422)


### Bug Fixes

* correct distributionUrl in maven-wrapper.properties ([dba9711](https://github.com/naviqore/public-transit-service/commit/dba9711ad1b82dcced5787fa0489a0a313c4e8c1))
* correct distributionUrl in maven-wrapper.properties ([c7ac8f1](https://github.com/naviqore/public-transit-service/commit/c7ac8f1cda9b5e60db90b25a130b68d6b6a7ffbe))
* **docker:** resolve build failure from deprecated base image ([581b8b7](https://github.com/naviqore/public-transit-service/commit/581b8b7199b9e0d59bad055e72c5312a0d975fd4))


### Code Refactoring

* standardize terminology for durations and transfers ([261e208](https://github.com/naviqore/public-transit-service/commit/261e208261000c5b7b7d5b3d7d02eaa273473097)), closes [#422](https://github.com/naviqore/public-transit-service/issues/422)

## [2.3.0](https://github.com/naviqore/public-transit-service/compare/v2.2.0...v2.3.0) (2025-09-02)


### Features

* **service:** relevance-based sorting for stop search ([56ac408](https://github.com/naviqore/public-transit-service/commit/56ac40892c6d77d3a760d63f44da5df95c758226))
* **service:** relevance-based sorting for stop search ([a9126d6](https://github.com/naviqore/public-transit-service/commit/a9126d60dfd206b14670d52e4134bccc543e5505))


### Documentation

* **service:** remove "e.g." in Javadoc comment since all options are listed ([e7c403d](https://github.com/naviqore/public-transit-service/commit/e7c403d9a8f9063fda04485f7cb483b737da4798))

## [2.2.0](https://github.com/naviqore/public-transit-service/compare/v2.1.1...v2.2.0) (2025-08-28)


### Features

* **app:** add support for S3-compatible services ([2ebffeb](https://github.com/naviqore/public-transit-service/commit/2ebffeb70c9c99ada83b6e495d110c1b765f2500))
* **app:** add support for S3-compatible services ([f8caf65](https://github.com/naviqore/public-transit-service/commit/f8caf6562fea44db752edaf91f00ce6fb6986928))
* **gtfs:** support for in-seat transfer types ([8e33a09](https://github.com/naviqore/public-transit-service/commit/8e33a096851fa8558c08b3ef7cd6d7faf4305a69))
* **gtfs:** support for in-seat transfer types ([adc9816](https://github.com/naviqore/public-transit-service/commit/adc98165c20cceb420acda009e72508c8924b520))

## [2.1.1](https://github.com/naviqore/public-transit-service/compare/v2.1.0...v2.1.1) (2025-05-08)


### Bug Fixes

* correct version tag of image ([90fbaf4](https://github.com/naviqore/public-transit-service/commit/90fbaf4224cd33fe8341e6ac34c2e6aff13a423b))
* correct version tag of image ([3a907c8](https://github.com/naviqore/public-transit-service/commit/3a907c8f50640beaaa9b3c69356002859309c85c))

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
