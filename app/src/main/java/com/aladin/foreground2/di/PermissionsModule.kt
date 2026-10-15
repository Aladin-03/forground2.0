package com.aladin.foreground2.di

import com.aladin.foreground2.core.permissions.AndroidPermissionChecker
import com.aladin.foreground2.core.permissions.PermissionChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface PermissionsModule {
    @Binds
    fun bindPermissionChecker(impl: AndroidPermissionChecker): PermissionChecker
}
