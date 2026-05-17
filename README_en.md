# Metro Subway System

[中文](README.md) | English<br>
[Discord](https://discord.com/invite/7tJeSZPZgv) | [QQ](https://pd.qq.com/s/1n3hpe4e7?b=9) | [Wiki](https://github.com/CubeX-MC/Metro/wiki)
![bstats](https://bstats.org/signatures/bukkit/Metro.svg)

Metro is a Minecraft subway transit plugin that lets administrators create automated subway lines. Players can right-click a powered rail to summon a minecart and ride automatically. Supports Paper 1.18+ and Folia.

![Demo](https://i.imgur.com/K335iWj.gif)

## Basic Concepts

* **Line** — Defines a subway route by an ordered list of stops
* **Stop** — A station area defined by two corner points in the world
* **StopPoint** — A powered rail within a Stop where players board
* **Transfer** — Connections from one Stop to other Lines

## Quick Start

### Create Your First Line

1. `/m line create line1 "Line 1"`
2. `/m line setcolor line1 &9`
3. `/m line setterminus line1 "East Terminus"`

### Create a Stop

1. `/m stop create station1 "Central Station"`
2. Left/right-click with a golden hoe to mark two corners
3. `/m stop setcorners station1`
4. Stand on a powered rail and run `/m stop setpoint`
5. `/m line addstop line1 station1`

### Player Usage

Right-click a powered rail inside a Stop to summon and board a minecart. The system handles travel, stops, and arrivals automatically.

## Full Command Reference

See the [Wiki Command Reference](https://github.com/CubeX-MC/Metro/wiki/Commands-en) for the complete command list.

## Permissions

| Permission | Default | Description |
| :--- | :--- | :--- |
| `metro.admin` | OP | Access all admin commands, includes teleport |
| `metro.use` | Everyone | Use the subway system |
| `metro.gui` | Everyone | Open `/m gui` |
| `metro.tp` | false | Teleport to stops |
| `metro.line.create` | false | Create new lines |
| `metro.stop.create` | false | Create new stops |
| `metro.portal.create` | false | Create minecart portals |

## Ownership & Permission Flow

* Newly created lines/stops/portals record the creator as owner.
* Use `trust`/`untrust`/`owner` subcommands to manage members.
* `/m stop link allow/deny` controls which lines may use a stop.
* Legacy data without ownership entries is server-owned (OP/`metro.admin` only).

## Addons

| Name | Author | Description |
| :--- | :----- | :---------- |
| [Metro-Altroutes](https://github.com/ALingqing/Metro-Altroutes) | ALingqing | Line status management · Suspension announcements · Alternate routes · Ride blocking |

> To add your addon, submit a Pull Request.

## Configuration Files

* `config.yml` — Global settings (display, sounds, minecart behavior)
* `lines.yml` — Line data storage
* `stops.yml` — Stop data storage
* `lang/` — Localization files

See the [Wiki](https://github.com/CubeX-MC/Metro/wiki) for detailed configuration docs.

## Build

```bash
mvn clean package
# Produces target/metro-<version>.jar
```

Single-module Maven project targeting Java 17 and 1.18+ servers.
