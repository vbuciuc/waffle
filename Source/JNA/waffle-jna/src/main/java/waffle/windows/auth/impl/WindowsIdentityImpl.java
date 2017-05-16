/**
 * Waffle (https://github.com/Waffle/waffle)
 *
 * Copyright (c) 2010-2017 Application Security, Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors: Application Security, Inc.
 */
package waffle.windows.auth.impl;

import java.util.ArrayList;
import java.util.List;

import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Advapi32Util.Account;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import com.sun.jna.platform.win32.WinNT.SECURITY_IMPERSONATION_LEVEL;
import com.sun.jna.platform.win32.WinNT.WELL_KNOWN_SID_TYPE;

import waffle.windows.auth.IWindowsAccount;
import waffle.windows.auth.IWindowsIdentity;
import waffle.windows.auth.IWindowsImpersonationContext;

/**
 * Windows Identity.
 * 
 * @author dblock[at]dblock[dot]org
 */
public class WindowsIdentityImpl implements IWindowsIdentity {

    /** The windows identity. */
    private final HANDLE windowsIdentity;

    /** The user groups. */
    private Account[]    userGroups;

    /** The windows account. */
    private Account      windowsAccount;

    /**
     * Instantiates a new windows identity impl.
     *
     * @param newWindowsIdentity
     *            the new windows identity
     */
    public WindowsIdentityImpl(final HANDLE newWindowsIdentity) {
        this.windowsIdentity = newWindowsIdentity;
    }

    /**
     * Gets the windows account.
     *
     * @return the windows account
     */
    private Account getWindowsAccount() {
        if (this.windowsAccount == null) {
            this.windowsAccount = Advapi32Util.getTokenAccount(this.windowsIdentity);
        }
        return this.windowsAccount;
    }

    /**
     * Gets the user groups.
     *
     * @return the user groups
     */
    private Account[] getUserGroups() {
        if (this.userGroups == null) {
            this.userGroups = Advapi32Util.getTokenGroups(this.windowsIdentity);
        }
        return this.userGroups.clone();
    }

    /*
     * (non-Javadoc)
     * @see waffle.windows.auth.IWindowsIdentity#getFqn()
     */
    @Override
    public String getFqn() {
        return this.getWindowsAccount().fqn;
    }

    /*
     * (non-Javadoc)
     * @see waffle.windows.auth.IWindowsIdentity#getGroups()
     */
    @Override
    public IWindowsAccount[] getGroups() {

        final Account[] groups = this.getUserGroups();

        final List<IWindowsAccount> result = new ArrayList<>(groups.length);
        for (final Account userGroup : groups) {
            final WindowsAccountImpl account = new WindowsAccountImpl(userGroup);
            result.add(account);
        }

        return result.toArray(new IWindowsAccount[0]);
    }

    /*
     * (non-Javadoc)
     * @see waffle.windows.auth.IWindowsIdentity#getSid()
     */
    @Override
    public byte[] getSid() {
        return this.getWindowsAccount().sid;
    }

    /*
     * (non-Javadoc)
     * @see waffle.windows.auth.IWindowsIdentity#getSidString()
     */
    @Override
    public String getSidString() {
        return this.getWindowsAccount().sidString;
    }

    /*
     * (non-Javadoc)
     * @see waffle.windows.auth.IWindowsIdentity#dispose()
     */
    @Override
    public void dispose() {
        if (this.windowsIdentity != null) {
            Kernel32.INSTANCE.CloseHandle(this.windowsIdentity);
        }
    }

    /*
     * (non-Javadoc)
     * @see waffle.windows.auth.IWindowsIdentity#impersonate()
     */
    @Override
    public IWindowsImpersonationContext impersonate() {
        return new WindowsIdentityImpersonationContextImpl(this.windowsIdentity);
    }

    /*
     * (non-Javadoc)
     * @see waffle.windows.auth.IWindowsIdentity#isGuest()
     */
    @Override
    public boolean isGuest() {
        for (final Account userGroup : this.getUserGroups()) {
            if (Advapi32Util.isWellKnownSid(userGroup.sid, WELL_KNOWN_SID_TYPE.WinBuiltinGuestsSid)) {
                return true;
            }
            if (Advapi32Util.isWellKnownSid(userGroup.sid, WELL_KNOWN_SID_TYPE.WinAccountDomainGuestsSid)) {
                return true;
            }
            if (Advapi32Util.isWellKnownSid(userGroup.sid, WELL_KNOWN_SID_TYPE.WinAccountGuestSid)) {
                return true;
            }
        }
        return Advapi32Util.isWellKnownSid(this.getSid(), WELL_KNOWN_SID_TYPE.WinAnonymousSid);
    }

    @Override
    public IWindowsIdentity duplicate() {
        HANDLEByReference duplicateHandle = new HANDLEByReference();

        if (!Advapi32.INSTANCE.DuplicateToken(this.windowsIdentity, SECURITY_IMPERSONATION_LEVEL.SecurityImpersonation,
                duplicateHandle)) {
            throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
        }

        return new WindowsIdentityImpl(duplicateHandle.getValue());
    }
}
