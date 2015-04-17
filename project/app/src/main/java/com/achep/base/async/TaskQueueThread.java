/*
 * Copyright (C) 2015 AChep@xda <artemchep@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package com.achep.base.async;

import android.support.annotation.NonNull;

import com.achep.base.interfaces.IThreadFinishable;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Artem Chepurnoy on 17.04.2015.
 */
public abstract class TaskQueueThread extends Thread implements IThreadFinishable {

    private final Queue<Runnable> mQueue = new ConcurrentLinkedQueue<>();
    private boolean mWaiting = false;
    private boolean mRunning = true;

    @Override
    public void finish() {
        finish(false);
    }

    public void finish(boolean clearAllTasks) {
        if (isAlive()) {
            mRunning = false;
            if (clearAllTasks) {
                synchronized (this) {
                    mQueue.clear();
                    if (mWaiting) mQueue.notifyAll();
                }
            }
            while (true) {
                try {
                    join();
                    break;
                } catch (InterruptedException e) { /* pretty please! */ }
            }
        }
    }

    @Override
    public void run() {
        super.run();

        Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        while (mRunning) {
            synchronized (this) {
                if (mQueue.isEmpty())
                    try {
                        // Wait for a next #sendEvent(Event),
                        // where this thread will be unlocked.
                        mWaiting = true;
                        wait();
                    } catch (InterruptedException ignored) {
                    } finally {
                        mWaiting = false;
                    }

                // Move all pending events to a local copy, so we don't need
                // to block main queue.
                while (!mQueue.isEmpty()) {
                    queue.add(mQueue.poll());
                }
            }

            if (isLost()) {
                mRunning = false;
                break;
            }

            Iterator<Runnable> iterator = queue.iterator();
            while (iterator.hasNext()) {
                Runnable runnable = iterator.next();
                // ~~
                runnable.run();
                // ~~
                iterator.remove();
            }
        }
    }

    public void sendTask(@NonNull Runnable runnable) {
        synchronized (this) {
            mQueue.add(runnable);

            // Release the thread lock if needed.
            if (mWaiting) mQueue.notifyAll();
        }
    }

    public void clearAllTasks() {
        synchronized (this) {
            mQueue.clear();
        }
    }

    protected abstract boolean isLost();

}