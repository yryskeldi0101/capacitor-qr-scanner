import { QrCodeScanner } from 'capacitor-qr-scanner';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    QrCodeScanner.echo({ value: inputValue })
}
