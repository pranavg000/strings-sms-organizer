package com.strings.app.domain.usecase

import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdoptLegacyWalletAccountsUseCaseTest {

    private fun legacyAccount(
        id: Long,
        name: String,
        type: AccountType = AccountType.WALLET,
        tail: String = ""
    ): Account = Account(
        id = id,
        bankName = name,
        accountTail = tail,
        accountType = type,
        displayName = name,
        bankCode = "",
        colorIndex = -1
    )

    @Test
    fun adoptsLegacyWalletRowWithoutDuplicate() {
        val legacy: Account = legacyAccount(id = 1L, name = "Zomato Money")
        val adoptions: List<WalletAdoption> = AdoptLegacyWalletAccountsUseCase.plan(listOf(legacy))
        assertEquals(1, adoptions.size)
        val adoption: WalletAdoption = adoptions.first()
        assertNull(adoption.duplicateAccountId)
        assertEquals(1L, adoption.adopted.id)
        assertEquals("ZOMATO", adoption.adopted.bankCode)
        assertEquals(AccountType.WALLET, adoption.adopted.accountType)
        assertTrue(adoption.adopted.isEnabled)
    }

    @Test
    fun mergesToggleCreatedDuplicateIntoLegacyRow() {
        val legacy: Account = legacyAccount(id = 1L, name = "Swiggy Money")
        val toggleCreated = Account(
            id = 7L,
            bankName = "Swiggy Money",
            accountTail = "",
            accountType = AccountType.WALLET,
            displayName = "Swiggy Money",
            bankCode = "SWIGGY",
            colorIndex = 4,
            isEnabled = true
        )
        val adoptions: List<WalletAdoption> =
            AdoptLegacyWalletAccountsUseCase.plan(listOf(legacy, toggleCreated))
        assertEquals(1, adoptions.size)
        val adoption: WalletAdoption = adoptions.first()
        assertEquals(7L, adoption.duplicateAccountId)
        assertEquals(1L, adoption.adopted.id)
        assertEquals("SWIGGY", adoption.adopted.bankCode)
        assertEquals(4, adoption.adopted.colorIndex)
        assertTrue(adoption.adopted.isEnabled)
    }

    @Test
    fun duplicateDisabledStateCarriesOverToAdoptedRow() {
        val legacy: Account = legacyAccount(id = 1L, name = "Swiggy Money")
        val toggleCreated = Account(
            id = 7L,
            bankName = "Swiggy Money",
            accountTail = "",
            accountType = AccountType.WALLET,
            displayName = "Swiggy Money",
            bankCode = "SWIGGY",
            colorIndex = 2,
            isEnabled = false
        )
        val adoptions: List<WalletAdoption> =
            AdoptLegacyWalletAccountsUseCase.plan(listOf(legacy, toggleCreated))
        assertEquals(false, adoptions.first().adopted.isEnabled)
    }

    @Test
    fun legacyColorIndexIsKeptWhenAssigned() {
        val legacy: Account = legacyAccount(id = 1L, name = "Zomato Money").copy(colorIndex = 8)
        val adoptions: List<WalletAdoption> = AdoptLegacyWalletAccountsUseCase.plan(listOf(legacy))
        assertEquals(8, adoptions.first().adopted.colorIndex)
    }

    @Test
    fun ignoresLegacyRowsThatAreNotToggleWallets() {
        val legacyCard: Account = legacyAccount(
            id = 2L,
            name = "ICICI Shapphiro Credit Card",
            type = AccountType.CREDIT_CARD,
            tail = "6001"
        )
        val adoptions: List<WalletAdoption> = AdoptLegacyWalletAccountsUseCase.plan(listOf(legacyCard))
        assertTrue(adoptions.isEmpty())
    }

    @Test
    fun ignoresConfiguredAccounts() {
        val configured = Account(
            id = 3L,
            bankName = "Zomato Money",
            accountTail = "",
            accountType = AccountType.WALLET,
            displayName = "Zomato Money",
            bankCode = "ZOMATO",
            colorIndex = 1
        )
        val adoptions: List<WalletAdoption> = AdoptLegacyWalletAccountsUseCase.plan(listOf(configured))
        assertTrue(adoptions.isEmpty())
    }

    @Test
    fun matchesWalletNameCaseInsensitively() {
        val legacy: Account = legacyAccount(id = 4L, name = "amazon pay")
        val adoptions: List<WalletAdoption> = AdoptLegacyWalletAccountsUseCase.plan(listOf(legacy))
        assertEquals(1, adoptions.size)
        assertEquals("AMAZON_PAY", adoptions.first().adopted.bankCode)
    }
}
