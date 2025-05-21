import { SplashScreen } from '@capacitor/splash-screen';
import { Capacitor, Permissions } from '@capacitor/core';
import { NearbyMultipeer } from '@squareetlabs/capacitor-nearby-multipeer';

window.customElements.define(
  'capacitor-welcome',
  class extends HTMLElement {
    constructor() {
      super();

      SplashScreen.hide();

      const root = this.attachShadow({ mode: 'open' });

      root.innerHTML = `
    <style>
      :host {
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
        display: block;
        width: 100%;
        height: 100%;
      }
      h1, h2, h3, h4, h5 {
        text-transform: uppercase;
      }
      .button {
        display: inline-block;
        padding: 10px;
        background-color: #73B5F6;
        color: #fff;
        font-size: 0.9em;
        border: 0;
        border-radius: 3px;
        text-decoration: none;
        cursor: pointer;
        margin: 5px;
      }
      .message-box {
        border: 1px solid #ddd;
        border-radius: 5px;
        padding: 10px;
        margin-top: 15px;
        height: 200px;
        overflow-y: auto;
      }
      .device-list {
        border: 1px solid #ddd;
        border-radius: 5px;
        padding: 10px;
        margin-top: 15px;
        height: 100px;
        overflow-y: auto;
      }
      .device-item {
        padding: 5px;
        border-bottom: 1px solid #eee;
        cursor: pointer;
      }
      .device-item:hover {
        background-color: #f5f5f5;
      }
      main {
        padding: 15px;
      }
      main hr { height: 1px; background-color: #eee; border: 0; }
      main h1 {
        font-size: 1.4em;
        text-transform: uppercase;
        letter-spacing: 1px;
      }
      main h2 {
        font-size: 1.1em;
      }
      main h3 {
        font-size: 0.9em;
      }
      main p {
        color: #333;
      }
      main pre {
        white-space: pre-line;
      }
      input {
        padding: 8px;
        border: 1px solid #ddd;
        border-radius: 3px;
        width: 100%;
        margin-bottom: 10px;
      }
    </style>
    <div>
      <capacitor-welcome-titlebar>
        <h1>Capacitor Nearby Multipeer</h1>
      </capacitor-welcome-titlebar>
      <main>
        <h2>Nearby Multipeer Demo</h2>
        <p>
          This demo shows how to use the Nearby Multipeer Capacitor plugin for peer-to-peer connectivity.
        </p>
        
        <div>
          <button class="button" id="init-btn">Initialize</button>
          <button class="button" id="request-permissions">Request Permissions</button>
        </div>
        
        <div>
          <button class="button" id="advertise-btn" disabled>Start Advertising</button>
          <button class="button" id="discovery-btn" disabled>Start Discovery</button>
          <button class="button" id="stop-btn" disabled>Stop All</button>
        </div>
        
        <h3>Discovered Devices</h3>
        <div class="device-list" id="devices-list">
          No devices found yet...
        </div>
        
        <h3>Send Message</h3>
        <div>
          <input type="text" id="message-input" placeholder="Enter message to send...">
          <button class="button" id="send-btn" disabled>Send</button>
        </div>
        
        <h3>Messages</h3>
        <div class="message-box" id="messages">
          No messages yet...
        </div>
      </main>
    </div>
    `;
    }

    connectedCallback() {
      const self = this;
      const initBtn = self.shadowRoot.querySelector('#init-btn');
      const permissionsBtn = self.shadowRoot.querySelector('#request-permissions');
      const advertiseBtn = self.shadowRoot.querySelector('#advertise-btn');
      const discoveryBtn = self.shadowRoot.querySelector('#discovery-btn');
      const stopBtn = self.shadowRoot.querySelector('#stop-btn');
      const sendBtn = self.shadowRoot.querySelector('#send-btn');
      const messageInput = self.shadowRoot.querySelector('#message-input');
      const devicesList = self.shadowRoot.querySelector('#devices-list');
      const messagesBox = self.shadowRoot.querySelector('#messages');
      
      // Store discovered endpoints
      let endpoints = {};
      let connectedEndpointId = null;
      
      // Helper to add log messages
      const addMessage = (message) => {
        const now = new Date();
        const time = now.toLocaleTimeString();
        messagesBox.innerHTML += `<div><strong>${time}</strong>: ${message}</div>`;
        messagesBox.scrollTop = messagesBox.scrollHeight;
      };
      
      // Request permissions for Android 12+
      permissionsBtn.addEventListener('click', async () => {
        try {
          await requestBluetoothPermissions();
          addMessage('Permissions requested');
        } catch (error) {
          addMessage(`Error requesting permissions: ${error.message}`);
        }
      });
      
      // Initialize the plugin
      initBtn.addEventListener('click', async () => {
        try {
          // Initialize with a unique service ID
          await NearbyMultipeer.initialize({ 
            serviceId: 'com.squareetlabs.nearbyexample' 
          });
          
          // Set up event listeners
          setupEventListeners();
          
          // Enable buttons
          advertiseBtn.disabled = false;
          discoveryBtn.disabled = false;
          stopBtn.disabled = false;
          
          addMessage('Nearby Multipeer initialized successfully');
        } catch (error) {
          addMessage(`Error initializing: ${error.message}`);
        }
      });
      
      // Start advertising
      advertiseBtn.addEventListener('click', async () => {
        try {
          await NearbyMultipeer.startAdvertising({ 
            displayName: 'My Device'
          });
          addMessage('Advertising started');
        } catch (error) {
          addMessage(`Error starting advertising: ${error.message}`);
        }
      });
      
      // Start discovery
      discoveryBtn.addEventListener('click', async () => {
        try {
          await NearbyMultipeer.startDiscovery();
          addMessage('Discovery started');
        } catch (error) {
          addMessage(`Error starting discovery: ${error.message}`);
        }
      });
      
      // Stop all activities
      stopBtn.addEventListener('click', async () => {
        try {
          await NearbyMultipeer.stopAdvertising();
          await NearbyMultipeer.stopDiscovery();
          await NearbyMultipeer.disconnect();
          devicesList.innerHTML = 'No devices found yet...';
          endpoints = {};
          connectedEndpointId = null;
          sendBtn.disabled = true;
          addMessage('All activities stopped');
        } catch (error) {
          addMessage(`Error stopping activities: ${error.message}`);
        }
      });
      
      // Send a message
      sendBtn.addEventListener('click', async () => {
        const message = messageInput.value.trim();
        if (!message || !connectedEndpointId) return;
        
        try {
          await NearbyMultipeer.sendMessage({
            endpointId: connectedEndpointId,
            data: message
          });
          addMessage(`You sent: ${message}`);
          messageInput.value = '';
        } catch (error) {
          addMessage(`Error sending message: ${error.message}`);
        }
      });
      
      // Set up event listeners for the plugin
      const setupEventListeners = async () => {
        // Listen for endpoint discovery
        await NearbyMultipeer.addListener('endpointFound', (event) => {
          endpoints[event.endpointId] = {
            name: event.endpointName,
            id: event.endpointId
          };
          updateDevicesList();
          addMessage(`Discovered device: ${event.endpointName}`);
        });
        
        // Listen for connection requests
        await NearbyMultipeer.addListener('connectionRequested', (event) => {
          addMessage(`Connection request from: ${event.endpointName}`);
          // Automatically accept connection
          NearbyMultipeer.acceptConnection({ endpointId: event.endpointId });
        });
        
        // Listen for connection results
        await NearbyMultipeer.addListener('connectionResult', (event) => {
          const status = event.status === 0 ? 'success' : 'failed';
          addMessage(`Connection ${status} for endpoint: ${event.endpointId}`);
          
          if (status === 'success') {
            connectedEndpointId = event.endpointId;
            sendBtn.disabled = false;
          }
        });
        
        // Listen for incoming messages
        await NearbyMultipeer.addListener('message', (event) => {
          const deviceName = endpoints[event.endpointId]?.name || event.endpointId;
          addMessage(`${deviceName}: ${event.data}`);
        });
        
        // Listen for disconnections
        await NearbyMultipeer.addListener('endpointLost', (event) => {
          addMessage(`Disconnected from: ${endpoints[event.endpointId]?.name || event.endpointId}`);
          delete endpoints[event.endpointId];
          
          if (connectedEndpointId === event.endpointId) {
            connectedEndpointId = null;
            sendBtn.disabled = true;
          }
          
          updateDevicesList();
        });
      };
      
      // Update the devices list UI
      const updateDevicesList = () => {
        if (Object.keys(endpoints).length === 0) {
          devicesList.innerHTML = 'No devices found yet...';
          return;
        }
        
        devicesList.innerHTML = '';
        Object.values(endpoints).forEach(endpoint => {
          const deviceElement = document.createElement('div');
          deviceElement.className = 'device-item';
          deviceElement.textContent = endpoint.name;
          deviceElement.addEventListener('click', async () => {
            if (connectedEndpointId === endpoint.id) {
              // Already connected, disconnect
              await NearbyMultipeer.disconnectFromEndpoint({ endpointId: endpoint.id });
              addMessage(`Disconnected from: ${endpoint.name}`);
              connectedEndpointId = null;
              sendBtn.disabled = true;
            } else {
              // Connect to this endpoint
              try {
                await NearbyMultipeer.connect({
                  endpointId: endpoint.id,
                  displayName: 'My Device'
                });
                addMessage(`Connecting to: ${endpoint.name}`);
              } catch (error) {
                addMessage(`Error connecting: ${error.message}`);
              }
            }
          });
          devicesList.appendChild(deviceElement);
        });
      };
      
      // Helper function to request Bluetooth permissions
      async function requestBluetoothPermissions() {
        if (Capacitor.getPlatform() === 'android') {
          const permissions = [
            'android.permission.BLUETOOTH_SCAN',
            'android.permission.BLUETOOTH_CONNECT',
            'android.permission.BLUETOOTH_ADVERTISE'
          ];
          
          for (const permission of permissions) {
            const status = await Permissions.query({ name: permission });
            if (status.state !== 'granted') {
              await Permissions.request({ name: permission });
            }
          }
        }
      }
    }
  }
);

window.customElements.define(
  'capacitor-welcome-titlebar',
  class extends HTMLElement {
    constructor() {
      super();
      const root = this.attachShadow({ mode: 'open' });
      root.innerHTML = `
    <style>
      :host {
        position: relative;
        display: block;
        padding: 15px 15px 15px 15px;
        text-align: center;
        background-color: #73B5F6;
      }
      ::slotted(h1) {
        margin: 0;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
        font-size: 0.9em;
        font-weight: 600;
        color: #fff;
      }
    </style>
    <slot></slot>
    `;
    }
  }
);
