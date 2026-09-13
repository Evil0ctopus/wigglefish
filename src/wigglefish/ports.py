"""Read-only serial port discovery."""

from dataclasses import dataclass

from serial.tools import list_ports


@dataclass(frozen=True)
class SerialPort:
    """A serial port and the metadata reported by the operating system."""

    device: str
    description: str
    hardware_id: str
    manufacturer: str | None
    product: str | None
    serial_number: str | None
    vid: int | None
    pid: int | None


def discover_ports() -> list[SerialPort]:
    """Return detected serial ports without opening or writing to them."""
    return [
        SerialPort(
            device=port.device,
            description=port.description,
            hardware_id=port.hwid,
            manufacturer=port.manufacturer,
            product=port.product,
            serial_number=port.serial_number,
            vid=port.vid,
            pid=port.pid,
        )
        for port in sorted(list_ports.comports(), key=lambda item: item.device)
    ]
