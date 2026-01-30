import React from 'react'

const Footer = () => {
    fetch("http://localhost:8080/products")
    .then(r => r.json())
    .then(json => console.log(json))
  return (
    <footer>voce chegou ao final do site</footer>
  )
}

export default Footer