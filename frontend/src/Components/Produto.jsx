import React from "react";

const Produto = ({name, amount, photoUrl}) => {
    return (
        <div>
            <h2>{name}</h2>
            <img src={photoUrl} alt="" />
            <p>Quantide: {amount}</p>
        </div>
    )
}

export default Produto