package com.squareetlabs.capacitor.nearbymultipeer;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Cola de comandos BLE para evitar operaciones concurrentes que pueden causar fallos
 */
public class BleCommandQueue {
    private static final String TAG = "BleCommandQueue";
    private final Queue<QueueItem<?>> commandQueue = new LinkedList<>();
    private boolean isExecuting = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long defaultTimeout = 10000; // 10 segundos por defecto

    /**
     * Encola un comando para ejecución secuencial
     * @param command Comando a ejecutar
     * @param <T> Tipo de retorno del comando
     * @return Resultado del comando
     */
    public synchronized <T> T enqueue(Callable<T> command) throws Exception {
        return enqueue(command, defaultTimeout);
    }

    /**
     * Encola un comando para ejecución secuencial con timeout específico
     * @param command Comando a ejecutar
     * @param timeout Timeout en milisegundos
     * @param <T> Tipo de retorno del comando
     * @return Resultado del comando
     */
    public synchronized <T> T enqueue(Callable<T> command, long timeout) throws Exception {
        QueueItem<T> queueItem = new QueueItem<>(command);
        commandQueue.add(queueItem);
        
        if (!isExecuting) {
            executeNext();
        }
        
        // Esperar a que se complete el comando o ocurra un timeout
        if (timeout > 0) {
            long startTime = System.currentTimeMillis();
            while (!queueItem.isCompleted() && System.currentTimeMillis() - startTime < timeout) {
                try {
                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new Exception("Command interrupted", e);
                }
            }
            
            if (!queueItem.isCompleted()) {
                throw new TimeoutException("Command timed out after " + timeout + "ms");
            }
        } else {
            // Esperar indefinidamente
            while (!queueItem.isCompleted()) {
                try {
                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new Exception("Command interrupted", e);
                }
            }
        }
        
        if (queueItem.getException() != null) {
            throw queueItem.getException();
        }
        
        return queueItem.getResult();
    }
    
    /**
     * Encola un comando sin retorno (Runnable)
     * @param command Comando a ejecutar
     */
    public synchronized void enqueueRunnable(Runnable command) throws Exception {
        enqueue(() -> {
            command.run();
            return null;
        });
    }

    private synchronized void executeNext() {
        if (commandQueue.isEmpty()) {
            isExecuting = false;
            return;
        }
        
        isExecuting = true;
        QueueItem<?> queueItem = commandQueue.poll();
        
        handler.post(() -> {
            try {
                Object result = queueItem.getCommand().call();
                queueItem.setResult(result);
            } catch (Exception e) {
                Log.e(TAG, "Error executing command", e);
                queueItem.setException(e);
            } finally {
                queueItem.setCompleted(true);
                executeNext();
            }
        });
    }
    
    /**
     * Establece el timeout por defecto para todos los comandos
     * @param defaultTimeout Timeout en milisegundos (0 para desactivar)
     */
    public void setDefaultTimeout(long defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }
    
    /**
     * Clase para almacenar un comando en la cola con su resultado/excepción
     */
    private static class QueueItem<T> {
        private final Callable<T> command;
        private T result;
        private Exception exception;
        private boolean completed = false;
        
        public QueueItem(Callable<T> command) {
            this.command = command;
        }
        
        public Callable<T> getCommand() {
            return command;
        }
        
        public T getResult() {
            return result;
        }
        
        public void setResult(Object result) {
            this.result = (T) result;
        }
        
        public Exception getException() {
            return exception;
        }
        
        public void setException(Exception exception) {
            this.exception = exception;
        }
        
        public boolean isCompleted() {
            return completed;
        }
        
        public void setCompleted(boolean completed) {
            this.completed = completed;
        }
    }
} 