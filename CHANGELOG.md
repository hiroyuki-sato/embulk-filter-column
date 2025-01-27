# 0.8.0 (2021-06-10)

Enhancements:

* Build it with the "org.embulk.embulk-plugins" Gradle plugin

# 0.7.1 (2017-08-24)

Enhancements:

* Support embulk < 0.8.29

# 0.7.0 (2017-08-24)

Changes:

* Follow new TimestampParser API of embulk >= 0.8.29.
  * Note that this plugin now requires embulk >= 0.8.29.

# 0.6.0 (2016-11-05)

Enhancements:

* Support bracket notation in json path (thanks to @kysnm)
* Autocomplete ancestor json paths

# 0.5.4 (2016-08-05)

Enhancements:

* raise ConfigException if json path ends with `[*]` (columns and add_columns)

# 0.5.3 (2016-06-21)

Changes:

* Remove parameter requirements that one of columns or add_columns or drop_columns are required

# 0.5.2 (2016-06-07)

Fixes:

* Fix add_columns for type: json with default to work

# 0.5.1 (2016-06-03)

Fixes:

* Fix JSONPath support

# 0.5.0 (2016-05-31)

Enhancements:

* Support src (rename or copy columns) for JSONPath (but only partially)

# 0.5.0.pre1 (2016-05-24)

Enhancements:

* Support JSONPath (like) name

# 0.4.0 (2016-02-01)

Enhancements:

* Support JSON type (thanks to joker1007)

Changes:

* Requires embulk >= 0.8.1

# 0.3.1 (2015-11-09)

Enhancements:

* Add copy column feature (thanks to hidepin)

# 0.3.0 (2015-10-27)

Enhancements:

* Add `default_timestamp_format` option
* Add `default_timezone` option

# 0.2.0 (2015-10-27)

Enhancements:

* Add `add_columns` option
* Add `drop_columns` option

# 0.1.6

Enhancements:

* Support Java 1.7

# 0.1.5

Enhancements:

* Support timestamp default

# 0.1.4

Enhancements:

* Add default option

# 0.1.3

Changes:

* Revert the name to `column` plugin ...

# 0.1.2

Changes:

* Change name to `select_column` plugin from `column` plugin

# 0.1.1

Enhancements:

* Speed up a bit

# 0.1.0

first version
