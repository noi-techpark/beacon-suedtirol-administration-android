package it.bz.beacon.adminapp.data.dao;

import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Update;

import java.util.ArrayList;

public interface BaseDao<T> {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(T object);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMultiple(ArrayList<T> objects);

    @Update
    void update(T object);

    @Delete
    void delete(T object);
}
