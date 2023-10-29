# Virtual Boltcard
This app uses Android HCE to emulate Boltcards. But only the POS-communication part. 
Configuring Boltcards using NFC is not supported.

## How it works
- if you start the app you have have to authenticate with your fingerprint or device-pin
this is because you have access to a lnbits wallet once the app is started. Also you can change the active card.
- with no card configured, you have 3 options to start:
1. create a lnbits account - creates an account, boltcard and lnurlp link on specified lbits-server.
    You can specify serverurl name and limits or just go with the defaults, currently legend.lnbits.com
    The app saves the **apikey and tracks the balance of your account**. the qrcode for the paylink is shown. 
    if you tap it a installed wallet that can handle lnurlp should open.
   **currently there is no export of the cards, so make sure if you setup a new LNBits-card to open the wallet and backup as usual**
2. import an existing boltcard from an lnbits wallet. Qrcode or url accepted.
3. manually setup an existing boltcard
- you can setup multiple cards (the "+"-bubble in the lower right of the screen is the only options currently.)
- active card is always the visual card. Just swipe another card in view to activate it. 
- there is currently no way to disable emulation, so you have to deactivate nfc to do that atm.
- emulation currently also works from the lockscreen. it stops if the screen is black, 
but once its on hce works and we are not quiet sure yet if thats a good or a bad thing.

## Security
- The app stores your boltcard url, keys and counter in encrypted settings-storage and an encrypted mssql database the password is randomly created on the first launch and saved to the encrypted settings-storage.
- Everyone with access to this data is able to access your sats.
- If you create an lnbits wallet from within the app, the api-keys are saved as well in the encrypted mssql database.
- I tried to get this reasonably safe, but i am **not** an experienced app-developer so i probably have completely missed that. In that case please leave an issue so we can fix it :-)
- a Boltcard is a offline device which keeps the cards url, keys and counter. You decrease security by putting this data directly onto your phone.

## Ref
- https://github.com/underwindfall/NFCAndroid
- https://github.com/boltcard
- https://www.boltcard.org/
