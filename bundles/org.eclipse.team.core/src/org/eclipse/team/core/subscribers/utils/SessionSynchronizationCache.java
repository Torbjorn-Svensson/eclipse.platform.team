/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.core.subscribers.utils;

import java.util.*;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.core.Assert;

/**
 * This cache keeps the sync butes in memory and does not persist them
 * over workbench invocations.
 */
public class SessionSynchronizationCache extends SynchronizationCache {

	private static final byte[] NO_REMOTE = new byte[0];
	
	private Map syncBytesCache = new HashMap();
	private Map membersCache = new HashMap();

	private Map getSyncBytesCache() {
		return syncBytesCache;
	}
	
	private byte[] internalGetSyncBytes(IResource resource) {
		return (byte[])getSyncBytesCache().get(resource);
	}
	
	private void internalAddToParent(IResource resource) {
		IContainer parent = resource.getParent();
		if (parent == null) return;
		List members = (List)membersCache.get(parent);
		if (members == null) {
			members = new ArrayList();
			membersCache.put(parent, members);
		}
		members.add(resource);
	}
	
	private void internalSetSyncInfo(IResource resource, byte[] bytes) {
		getSyncBytesCache().put(resource, bytes);
		internalAddToParent(resource);
	}

	private void internalRemoveFromParent(IResource resource) {
		IContainer parent = resource.getParent();
		List members = (List)membersCache.get(parent);
		if (members != null) {
			members.remove(resource);
			if (members.isEmpty()) {
				membersCache.remove(parent);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.utils.SynchronizationCache#dispose()
	 */
	public void dispose() {
		syncBytesCache.clear();
		membersCache.clear();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.utils.SynchronizationCache#getSyncBytes(org.eclipse.core.resources.IResource)
	 */
	public byte[] getSyncBytes(IResource resource) throws TeamException {
		byte[] syncBytes = internalGetSyncBytes(resource);
		if (syncBytes != null && equals(syncBytes, NO_REMOTE)) {
			// If it is known that there is no remote, return null
			return null;
		}
		return syncBytes;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.utils.SynchronizationCache#setSyncBytes(org.eclipse.core.resources.IResource, byte[])
	 */
	public boolean setSyncBytes(IResource resource, byte[] bytes) throws TeamException {
		Assert.isNotNull(bytes);
		byte[] oldBytes = internalGetSyncBytes(resource);
		if (oldBytes != null && equals(oldBytes, bytes)) return false;
		internalSetSyncInfo(resource, bytes);
		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.utils.SynchronizationCache#removeSyncBytes(org.eclipse.core.resources.IResource, int)
	 */
	public boolean removeSyncBytes(IResource resource, int depth) throws TeamException {
		if (getSyncBytesCache().containsKey(resource)) {
			if (depth != IResource.DEPTH_ZERO) {
				IResource[] members = members(resource);
				for (int i = 0; i < members.length; i++) {
					IResource child = members[i];
					removeSyncBytes(child, (depth == IResource.DEPTH_INFINITE) ? IResource.DEPTH_INFINITE: IResource.DEPTH_ZERO);
				}
			}
			getSyncBytesCache().remove(resource);
			internalRemoveFromParent(resource);
			return true;
		}
		return false;
	}

	/**
	 * Return the children of the given resource that have sync bytes in this cache.
	 * @param resource the parent resource
	 * @return the memebrs who have sync bytes in this cache
	 */
	public IResource[] members(IResource resource) {
		List members = (List)membersCache.get(resource);
		if (members == null) {
			return new IResource[0];
		}
		return (IResource[]) members.toArray(new IResource[members.size()]);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.utils.SynchronizationCache#isRemoteKnown(org.eclipse.core.resources.IResource)
	 */
	public boolean isRemoteKnown(IResource resource) throws TeamException {
		return internalGetSyncBytes(resource) != null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.utils.SynchronizationCache#setRemoteDoesNotExist(org.eclipse.core.resources.IResource)
	 */
	public boolean setRemoteDoesNotExist(IResource resource) throws TeamException {
		return setSyncBytes(resource, NO_REMOTE);
	}
}
