package com.rarible.ethereum.common

import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Bytes
import io.daonomic.rpc.domain.Word
import org.apache.commons.lang3.RandomUtils
import org.web3jold.crypto.Hash
import org.web3jold.crypto.Keys
import org.web3jold.crypto.Sign
import org.web3jold.utils.Numeric
import scalether.domain.Address
import scalether.util.Hex
import java.math.BigInteger

fun BigInteger.toHexString(): String {
    return Hex.prefixed(this.toByteArray())
}

fun String.toAddress(): Address {
    return Address.apply(this)
}

fun keccak256(bytes: ByteArray): Word = Word(Hash.sha3(bytes))

fun keccak256(bytes: Bytes): Word = keccak256(bytes.bytes())

fun keccak256(str: String): Word = keccak256(str.toByteArray(Charsets.US_ASCII))

fun Sign.SignatureData.toBinary(): Binary = Binary.apply(this.r).add(this.s).add(byteArrayOf(this.v))

fun generateNewKeys(): NewKeys {
    val privateKey = Numeric.toBigInt(RandomUtils.nextBytes(32))
    val publicKey = Sign.publicKeyFromPrivate(privateKey)
    val signer = Address.apply(Keys.getAddressFromPrivateKey(privateKey))
    return NewKeys(privateKey, publicKey, signer)
}

data class NewKeys(
    val privateKey: BigInteger,
    val publicKey: BigInteger,
    val address: Address
)
