/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.snippet.wifi.aware;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;

/**
 * Utility class for serializing and deserializing Parcel and Serializable objects to and from
 * Strings.
 */
public class SerializationUtil {


    /**
     * Serializes a Parcelable object to a Base64 encoded string.
     *
     * @param parcelable The Parcelable object to serialize.
     * @return Base64 encoded string of the serialized Parcelable object.
     */
    public static String parcelableToString(Parcelable parcelable) {
        Parcel parcel = Parcel.obtain();
        parcelable.writeToParcel(parcel, 0); // Ensure this object implements Parcelable
        byte[] bytes = parcel.marshall(); // Convert the Parcel into a byte array
        parcel.recycle(); // Recycle the Parcel to free up resources
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    /**
     * Deserializes a Base64 encoded string back into a Parcelable object.
     *
     * @param input   The Base64 encoded string of the serialized Parcelable object.
     * @param creator The CREATOR field of the Parcelable object, used to recreate the object.
     * @param <T>     The type of the Parcelable object.
     * @return A Parcelable object recreated from the string.
     */
    public static <T> T stringToParcelable(String input, Parcelable.Creator<T> creator) {
        byte[] bytes = Base64.decode(input, Base64.DEFAULT);
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length); // Unmarshall the byte array into a Parcel
        parcel.setDataPosition(0); // Reset the position to the start of the Parcel data
        T result = creator.createFromParcel(parcel); // Recreate the Parcelable object
        parcel.recycle(); // Recycle the Parcel to free up resources
        return result;
    }
}
